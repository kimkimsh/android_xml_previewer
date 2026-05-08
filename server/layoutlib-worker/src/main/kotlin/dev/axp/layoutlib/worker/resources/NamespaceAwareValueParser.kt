package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import java.nio.file.Path
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

/**
 * StAX-based values.xml parser. Handles dimen / integer / bool / color / string /
 * style / attr / declare-styleable. Namespace + sourcePackage are constructor
 * arguments so the same parser ingests framework, sample-app, and AAR resources
 * with proper attribution.
 *
 * Two structural invariants the loop preserves:
 *  1. declare-styleable nested attr is first-wins relative to top-level — the
 *     top-level entry wins. Mirrors AAPT2's first-wins policy and prevents
 *     declare-styleable references from masking real attr definitions.
 *  2. parseInternal scans for the root <resources> element first, then
 *     dispatches on its direct children only. END_TAG match on <resources>
 *     terminates. A depth counter would fail because handleSimpleValue,
 *     handleStyle, and handleDeclareStyleable each consume their own
 *     END_ELEMENT, so the outer loop never observes the depth-- it would need
 *     and silently drops every sibling after the first.
 *
 * handleStyle / handleDeclareStyleable use skipElement (depth-aware) for any
 * unknown START_ELEMENT to avoid early-exit on inner </style>-named tokens
 * nested inside an unknown child. Top-level
 * <item type="X" name="Y">Z</item> is also dispatched directly because Material3
 * AARs use it heavily for typed resource values.
 */
internal object NamespaceAwareValueParser
{

    private val mFactory: XMLInputFactory = XMLInputFactory.newInstance().apply {
        setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false)
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
        setProperty("javax.xml.stream.isSupportingExternalEntities", false)
    }

    fun parse(path: Path, namespace: ResourceNamespace, sourcePackage: String?): List<ParsedNsEntry>
    {
        return path.toFile().inputStream().use { input ->
            val reader = mFactory.createXMLStreamReader(input)
            try
            {
                parseInternal(reader, namespace, sourcePackage)
            }
            catch (e: Exception)
            {
                throw IllegalStateException("$path parse failed: ${e.message}", e)
            }
            finally
            {
                reader.close()
            }
        }
    }

    private fun parseInternal(
        reader: XMLStreamReader,
        namespace: ResourceNamespace,
        sourcePackage: String?,
    ): List<ParsedNsEntry>
    {
        val entries = mutableListOf<ParsedNsEntry>()
        val seenAttrNames = HashSet<String>()

        // Scan forward until the root <resources> START_ELEMENT.
        var event = if (reader.hasNext()) reader.next() else return entries
        while (event != XMLStreamConstants.START_ELEMENT && reader.hasNext())
        {
            event = reader.next()
        }
        if (event != XMLStreamConstants.START_ELEMENT) return entries
        if (reader.localName != TAG_RESOURCES)
        {
            throw IllegalStateException("root element must be <$TAG_RESOURCES>: ${reader.localName}")
        }

        // Walk the direct children of <resources>. Terminate on the matching
        // </resources> END_TAG. A depth counter would not work here because
        // handleSimpleValue, handleStyle, and handleDeclareStyleable each
        // consume their own END_ELEMENT, so the outer loop never observes the
        // depth-- it would need; the result would be every sibling after the
        // first being silently dropped.
        while (reader.hasNext())
        {
            event = reader.next()
            when (event)
            {
                XMLStreamConstants.END_DOCUMENT -> return entries
                XMLStreamConstants.END_ELEMENT ->
                {
                    if (reader.localName == TAG_RESOURCES) return entries
                }
                XMLStreamConstants.START_ELEMENT ->
                {
                    when (reader.localName)
                    {
                        TAG_DIMEN, TAG_INTEGER, TAG_BOOL, TAG_COLOR, TAG_STRING, TAG_FRACTION ->
                            handleSimpleValue(reader, namespace, sourcePackage)?.let { entries += it }
                        TAG_STYLE ->
                            handleStyle(reader, namespace, sourcePackage)?.let { entries += it }
                        TAG_ATTR ->
                        {
                            val name = reader.getAttributeValue(null, ATTR_NAME) ?: ""
                            // parseAttrChildren collects <enum> / <flag> child entries
                            // and consumes through the matching </attr> END_ELEMENT.
                            // A self-closing <attr name="X"/> still hits that matching
                            // END_ELEMENT immediately — no skipElement needed afterwards.
                            val attrChildren = parseAttrChildren(reader)
                            // Cross-namespace attr ref (e.g. <attr name="android:visible" />)
                            // is a reference to an attr defined in another namespace, not a
                            // new definition. Emitting it would make ResourceReference's
                            // constructor throw "Qualified name is not allowed". AAPT2 applies
                            // the same policy. Skip every name containing ':' so future
                            // prefixes (app:, androidx:, etc.) are handled identically.
                            if (name.isNotEmpty() && !name.contains(NS_NAME_SEPARATOR_CHAR) && seenAttrNames.add(name))
                            {
                                entries += ParsedNsEntry.AttrDef(
                                    name,
                                    namespace,
                                    attrChildren.first,
                                    attrChildren.second,
                                    sourcePackage,
                                )
                            }
                        }
                        TAG_DECLARE_STYLEABLE ->
                            handleDeclareStyleable(reader, namespace, sourcePackage, seenAttrNames, entries)
                        TAG_ITEM ->
                        {
                            // Top-level <item type="X" name="Y">Z</item> is the
                            // typed-resource form Material3 AARs use heavily,
                            // e.g. <item type="dimen" name="design_appbar_elevation">4dp</item>.
                            val typeAttr = reader.getAttributeValue(null, ATTR_TYPE)
                            val resType = typeAttr?.let { ResourceType.fromXmlValue(it) }
                            val name = reader.getAttributeValue(null, ATTR_NAME) ?: ""
                            // readElementText supports mixed content: child START_ELEMENT
                            // (e.g. <xliff:g>) markup is stripped while its inner text
                            // accumulates into the result.
                            val value = readElementText(reader)
                            if (resType != null && name.isNotEmpty())
                            {
                                entries += ParsedNsEntry.SimpleValue(resType, name, value, namespace, sourcePackage)
                            }
                        }
                        else -> skipElement(reader)  // unknown top-level (e.g. <public>, <eat-comment>) skip
                    }
                }
                else -> { /* text / comment / whitespace ignore */ }
            }
        }
        return entries
    }

    private fun handleSimpleValue(
        reader: XMLStreamReader,
        namespace: ResourceNamespace,
        sourcePackage: String?,
    ): ParsedNsEntry.SimpleValue?
    {
        val name = reader.getAttributeValue(null, ATTR_NAME) ?: return null
        val type = when (reader.localName)
        {
            TAG_DIMEN -> ResourceType.DIMEN
            TAG_INTEGER -> ResourceType.INTEGER
            TAG_BOOL -> ResourceType.BOOL
            TAG_COLOR -> ResourceType.COLOR
            TAG_STRING -> ResourceType.STRING
            TAG_FRACTION -> ResourceType.FRACTION
            else -> return null
        }
        // readElementText handles mixed content; StAX's elementText would throw on
        // real AAR strings like <string>Hello <b>world</b></string> or
        // <xliff:g> placeholders.
        val value = readElementText(reader)
        return ParsedNsEntry.SimpleValue(type, name, value, namespace, sourcePackage)
    }

    private fun handleStyle(
        reader: XMLStreamReader,
        namespace: ResourceNamespace,
        sourcePackage: String?,
    ): ParsedNsEntry.StyleDef?
    {
        val name = reader.getAttributeValue(null, ATTR_NAME) ?: return null
        val parent = reader.getAttributeValue(null, ATTR_PARENT)
        val items = mutableListOf<ParsedNsEntry.StyleDef.StyleItem>()
        while (reader.hasNext())
        {
            val event = reader.next()
            if (event == XMLStreamConstants.START_ELEMENT && reader.localName == TAG_ITEM)
            {
                val itemName = reader.getAttributeValue(null, ATTR_NAME) ?: ""
                // readElementText supports mixed content for style item bodies, since
                // real Material AARs sometimes embed inline markup (<xliff:g>, etc.)
                // inside style items. Style item values are reference / literal
                // payloads, not display text: collapse the surrounding whitespace
                // that AAR XML formatters introduce (`<item ...>\n  @animator/...\n</item>`
                // matches AAPT2's normalised form).
                val itemValue = readElementText(reader).trim()
                if (itemName.isNotEmpty()) items += ParsedNsEntry.StyleDef.StyleItem(itemName, itemValue)
            }
            else if (event == XMLStreamConstants.START_ELEMENT)
            {
                // Depth-aware skip for unknown START_ELEMENT inside <style>. A simple
                // break would exit early on any inner </style>-named token nested
                // inside an unknown child.
                skipElement(reader)
            }
            else if (event == XMLStreamConstants.END_ELEMENT && reader.localName == TAG_STYLE)
            {
                break
            }
        }
        return ParsedNsEntry.StyleDef(name, parent, items, namespace, sourcePackage)
    }

    private fun handleDeclareStyleable(
        reader: XMLStreamReader,
        namespace: ResourceNamespace,
        sourcePackage: String?,
        seen: MutableSet<String>,
        entries: MutableList<ParsedNsEntry>,
    )
    {
        while (reader.hasNext())
        {
            val event = reader.next()
            if (event == XMLStreamConstants.START_ELEMENT && reader.localName == TAG_ATTR)
            {
                val name = reader.getAttributeValue(null, ATTR_NAME) ?: ""
                // Nested <attr> inside declare-styleable uses the same child-capture
                // policy as top-level. Defining enum/flag values inside
                // declare-styleable is rare but does occur in framework and some AARs.
                val attrChildren = parseAttrChildren(reader)
                // Cross-namespace attr ref inside declare-styleable
                // (e.g. <attr name="android:visible" />) declares that the styleable
                // includes a framework attr — it is not a new definition. This pattern
                // is extremely common in real Material/AppCompat AARs. Skip every name
                // containing ':' to match the top-level policy.
                if (name.isNotEmpty() && !name.contains(NS_NAME_SEPARATOR_CHAR) && seen.add(name))
                {
                    entries += ParsedNsEntry.AttrDef(
                        name,
                        namespace,
                        attrChildren.first,
                        attrChildren.second,
                        sourcePackage,
                    )
                }
            }
            else if (event == XMLStreamConstants.START_ELEMENT)
            {
                // Depth-aware skip for any non-<attr> START_ELEMENT inside
                // declare-styleable.
                skipElement(reader)
            }
            else if (event == XMLStreamConstants.END_ELEMENT && reader.localName == TAG_DECLARE_STYLEABLE)
            {
                break
            }
        }
    }

    /**
     * Collects <enum> / <flag> child entries of an <attr> definition, depth-aware.
     *
     * Cursor contract: caller positions reader at the <attr> START_ELEMENT; this
     * helper consumes through the matching END_ELEMENT (same termination as
     * skipElement). Only direct enum / flag children are captured.
     *
     * Value parsing: framework attrs.xml uses a mix of decimal and hex literals
     * (~60% hex, including 32-bit unsigned masks). Long.decode(...).toInt()
     * handles both conventions plus signed-32-bit corners (Int.MIN_VALUE =
     * 0x80000000, -1 = 0xffffffff, plain "-1" decimal). Unparseable values are
     * silently skipped to keep the parser resilient against broken AARs.
     *
     * enum + flag co-occurrence is not observed in the framework attrs census;
     * the caller still receives both maps separately so the calling site decides
     * semantics rather than forcing mutual exclusion here.
     *
     * Returned Pair: first = enums, second = flags.
     */
    private fun parseAttrChildren(reader: XMLStreamReader): Pair<Map<String, Int>, Map<String, Int>>
    {
        val enums = LinkedHashMap<String, Int>()
        val flags = LinkedHashMap<String, Int>()
        var depth = 1
        while (reader.hasNext() && depth > 0)
        {
            when (reader.next())
            {
                XMLStreamConstants.START_ELEMENT ->
                {
                    depth++
                    if (depth == 2)
                    {
                        val tag = reader.localName
                        if (tag == TAG_ENUM || tag == TAG_FLAG)
                        {
                            val childName = reader.getAttributeValue(null, ATTR_NAME)
                            val rawValue = reader.getAttributeValue(null, ATTR_VALUE)
                            if (childName != null && rawValue != null)
                            {
                                val parsed = parseAttrValueLiteral(rawValue)
                                if (parsed != null)
                                {
                                    if (tag == TAG_ENUM) enums[childName] = parsed else flags[childName] = parsed
                                }
                            }
                        }
                    }
                }
                XMLStreamConstants.END_ELEMENT -> depth--
            }
        }
        return enums to flags
    }

    /**
     * Parses an <enum value="..."/> or <flag value="..."/> literal. Long.decode
     * accepts decimal, hex, and octal forms and survives 32-bit unsigned hex
     * like 0x80000000 / 0xffffffff (the Long → Int narrow yields the expected
     * signed 32-bit value). Integer.decode would throw NumberFormatException on
     * those large hex masks, dropping framework attrs.xml flag entries. Returns
     * null on parse failure so a malformed AAR cannot crash the loader.
     */
    private fun parseAttrValueLiteral(raw: String): Int? = try
    {
        java.lang.Long.decode(raw.trim()).toInt()
    }
    catch (e: NumberFormatException)
    {
        null
    }

    /**
     * Skip helper that consumes the current element through its matching
     * END_ELEMENT, including any nested elements. Cursor contract: reader is
     * positioned at a START_ELEMENT on entry; depth starts at 1 and tracks
     * nesting (++ on START_ELEMENT, -- on END_ELEMENT) until it returns to 0.
     */
    private fun skipElement(reader: XMLStreamReader)
    {
        var depth = 1
        while (depth > 0 && reader.hasNext())
        {
            when (reader.next())
            {
                XMLStreamConstants.START_ELEMENT -> depth++
                XMLStreamConstants.END_ELEMENT -> depth--
            }
        }
    }

    /**
     * Mixed-content text reader. StAX's XMLStreamReader.elementText() throws
     * XMLStreamException ("elementGetText() function expects text only element
     * but START_ELEMENT was encountered") on any nested markup, but real
     * Android AAR values.xml regularly carries mixed content:
     *  - <string>Hello <b>world</b></string> (inline HTML markup)
     *  - <string>Page <xliff:g id="num">%1$d</xliff:g></string> (translation placeholder)
     *  - styled spans (<a>, <u>, <i>) inside string resources
     *
     * This helper accumulates every CHARACTERS / CDATA event into the buffer
     * and walks through child START_ELEMENT openings (markup itself is
     * stripped; the text inside the child still accumulates). Entity
     * references, comments, and processing instructions are silently ignored.
     *
     * Cursor contract: reader is positioned at the parent START_ELEMENT
     * (<dimen>, <string>, <item>, ...). Returns when depth hits 0 — the
     * matching END_ELEMENT.
     */
    private fun readElementText(reader: XMLStreamReader): String
    {
        val sb = StringBuilder()
        var depth = 1
        while (reader.hasNext() && depth > 0)
        {
            when (reader.next())
            {
                XMLStreamConstants.CHARACTERS -> sb.append(reader.text)
                XMLStreamConstants.CDATA -> sb.append(reader.text)
                XMLStreamConstants.START_ELEMENT -> depth++
                XMLStreamConstants.END_ELEMENT -> depth--
                // entity ref / comment / PI silently ignored.
            }
        }
        return sb.toString()
    }

    // Tag / attribute name constants — CLAUDE.md "Zero Tolerance for Magic
    // Numbers/Strings" applies even to single-word XML tokens.
    private const val TAG_RESOURCES = "resources"
    private const val TAG_DIMEN = "dimen"
    private const val TAG_INTEGER = "integer"
    private const val TAG_BOOL = "bool"
    private const val TAG_COLOR = "color"
    private const val TAG_STRING = "string"
    private const val TAG_FRACTION = "fraction"
    private const val TAG_STYLE = "style"
    private const val TAG_ATTR = "attr"
    private const val TAG_ITEM = "item"
    private const val TAG_DECLARE_STYLEABLE = "declare-styleable"
    private const val TAG_ENUM = "enum"
    private const val TAG_FLAG = "flag"

    private const val ATTR_NAME = "name"
    private const val ATTR_PARENT = "parent"
    private const val ATTR_TYPE = "type"
    private const val ATTR_VALUE = "value"

    /**
     * Cross-namespace ref separator inside attr names (e.g. android:visible,
     * app:foo). Any attr name containing ':' is a reference, not a local
     * definition — AttrDef emit skips it. CLAUDE.md "Zero Tolerance for Magic
     * Numbers/Strings" extends the named-constant policy to single-character
     * literals.
     */
    private const val NS_NAME_SEPARATOR_CHAR = ':'
}
