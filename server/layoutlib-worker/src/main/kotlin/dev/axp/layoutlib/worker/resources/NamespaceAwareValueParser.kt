package dev.axp.layoutlib.worker.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import java.nio.file.Path
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

/**
 * W3D4 §3.1 #4: W3D1 FrameworkValueParser 의 namespace + sourcePackage 인자 일반화.
 * 로직: StAX 기반 values.xml parser. dimen/integer/bool/color/string/style/attr/declare-styleable 처리.
 * declare-styleable nested attr first-wins (W3D1 F1 정책 보존 — top-level 이 우선).
 *
 * W3D4 T2 follow-up (W3D1 패턴 inherit): handleStyle / handleDeclareStyleable 의 unknown
 * START_ELEMENT 는 skipElement helper 로 depth-aware skip. parseInternal 의 top-level 분기에
 * top-level <item type="X" name="Y">Z</item> case 추가 (Material3 AAR 흔한 패턴).
 *
 * W3D4 T2 second-fix: parseInternal 이 outer depth counter 로 top-level 을 식별했었으나,
 * handleSimpleValue/handleStyle/handleDeclareStyleable 가 자체 END_ELEMENT 까지 consume → outer
 * loop 의 depth-- 가 fire 안 됨 → 두 번째 sibling 부터 silently dropped (real Material3 회귀).
 * 해결: W3D1 FrameworkValueParser 의 state-machine 패턴 차용 — root <resources> scan 후 direct
 * children 만 dispatch, root END_TAG match 로 종료. depth counter 제거.
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
                throw IllegalStateException("$path 파싱 실패: ${e.message}", e)
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

        // <resources> 까지 scan (W3D1 FrameworkValueParser 패턴 차용).
        var event = if (reader.hasNext()) reader.next() else return entries
        while (event != XMLStreamConstants.START_ELEMENT && reader.hasNext())
        {
            event = reader.next()
        }
        if (event != XMLStreamConstants.START_ELEMENT) return entries
        if (reader.localName != TAG_RESOURCES)
        {
            throw IllegalStateException("root element 는 <$TAG_RESOURCES> 여야 함: ${reader.localName}")
        }

        // <resources> direct children 순회 — root END_TAG match 로 종료.
        // depth counter 를 쓰지 않는 이유: handleSimpleValue/handleStyle/handleDeclareStyleable 가
        // 자체 END_ELEMENT 까지 consume → outer 가 본인 depth-- 를 못 보면 두 번째 sibling 부터 dropped.
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
                            if (name.isNotEmpty() && seenAttrNames.add(name))
                            {
                                entries += ParsedNsEntry.AttrDef(name, namespace, sourcePackage)
                            }
                            // top-level <attr> 가 자식 (enum/flag) 가질 수 있음 → depth-aware skip.
                            // 자식 없는 self-closing (`<attr name="X"/>`) 도 skipElement 가 즉시 종료.
                            skipElement(reader)
                        }
                        TAG_DECLARE_STYLEABLE ->
                            handleDeclareStyleable(reader, namespace, sourcePackage, seenAttrNames, entries)
                        TAG_ITEM ->
                        {
                            // W3D1 패턴 inherit: top-level <item type="X" name="Y">Z</item>
                            // (Material3 AAR 흔한 패턴, e.g. <item type="dimen" name="design_appbar_elevation">4dp</item>)
                            val typeAttr = reader.getAttributeValue(null, ATTR_TYPE)
                            val resType = typeAttr?.let { ResourceType.fromXmlValue(it) }
                            val name = reader.getAttributeValue(null, ATTR_NAME) ?: ""
                            val value = reader.elementText ?: ""
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
        val value = reader.elementText ?: ""
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
                val itemValue = reader.elementText ?: ""
                if (itemName.isNotEmpty()) items += ParsedNsEntry.StyleDef.StyleItem(itemName, itemValue)
            }
            else if (event == XMLStreamConstants.START_ELEMENT)
            {
                // W3D1 패턴 inherit: unknown START_ELEMENT 는 depth-aware skip.
                // 직전 break 방식은 inner </style> 에서 잘못 종료될 위험 (depth-unaware exit).
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
                if (name.isNotEmpty() && seen.add(name))
                {
                    entries += ParsedNsEntry.AttrDef(name, namespace, sourcePackage)
                }
                // attr 는 enum/flag 자식을 가질 수 있음 — 자식 element 가 있을 경우 skipElement 가
                // depth-aware 로 종료까지 소비. 없으면 즉시 END_ELEMENT 만나서 0 으로 종료.
                skipElement(reader)
            }
            else if (event == XMLStreamConstants.START_ELEMENT)
            {
                // W3D1 패턴 inherit: non-attr unknown START_ELEMENT 는 depth-aware skip.
                skipElement(reader)
            }
            else if (event == XMLStreamConstants.END_ELEMENT && reader.localName == TAG_DECLARE_STYLEABLE)
            {
                break
            }
        }
    }

    /**
     * W3D1 FrameworkValueParser.skipTag 의 StAX 등가 helper.
     * 호출 시 reader 는 START_ELEMENT 위치이고, 매칭 END_ELEMENT 까지 nested 포함하여 소비.
     * depth=1 부터 시작 — START_ELEMENT 시 ++, END_ELEMENT 시 --, 0 도달 시 return.
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

    // 태그/속성 이름 상수 — CLAUDE.md zero-magic-strings (W3D1 FrameworkValueParser 패턴 inherit).
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

    private const val ATTR_NAME = "name"
    private const val ATTR_PARENT = "parent"
    private const val ATTR_TYPE = "type"
}
