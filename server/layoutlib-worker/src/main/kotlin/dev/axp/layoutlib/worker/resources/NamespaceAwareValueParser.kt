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
        var depth = 0
        while (reader.hasNext())
        {
            val event = reader.next()
            when (event)
            {
                XMLStreamConstants.START_ELEMENT ->
                {
                    depth++
                    if (depth == 2)
                    {
                        when (reader.localName)
                        {
                            "dimen", "integer", "bool", "color", "string", "fraction" ->
                                handleSimpleValue(reader, namespace, sourcePackage)?.let { entries += it }
                            "style" ->
                                handleStyle(reader, namespace, sourcePackage)?.let { entries += it }
                            "attr" ->
                            {
                                val name = reader.getAttributeValue(null, "name") ?: ""
                                if (name.isNotEmpty() && seenAttrNames.add(name))
                                {
                                    entries += ParsedNsEntry.AttrDef(name, namespace, sourcePackage)
                                }
                            }
                            "declare-styleable" ->
                                handleDeclareStyleable(reader, namespace, sourcePackage, seenAttrNames, entries)
                        }
                    }
                }
                XMLStreamConstants.END_ELEMENT -> depth--
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
        val name = reader.getAttributeValue(null, "name") ?: return null
        val type = when (reader.localName)
        {
            "dimen" -> ResourceType.DIMEN
            "integer" -> ResourceType.INTEGER
            "bool" -> ResourceType.BOOL
            "color" -> ResourceType.COLOR
            "string" -> ResourceType.STRING
            "fraction" -> ResourceType.FRACTION
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
        val name = reader.getAttributeValue(null, "name") ?: return null
        val parent = reader.getAttributeValue(null, "parent")
        val items = mutableListOf<ParsedNsEntry.StyleDef.StyleItem>()
        while (reader.hasNext())
        {
            val event = reader.next()
            if (event == XMLStreamConstants.START_ELEMENT && reader.localName == "item")
            {
                val itemName = reader.getAttributeValue(null, "name") ?: ""
                val itemValue = reader.elementText ?: ""
                if (itemName.isNotEmpty()) items += ParsedNsEntry.StyleDef.StyleItem(itemName, itemValue)
            }
            else if (event == XMLStreamConstants.END_ELEMENT && reader.localName == "style")
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
            if (event == XMLStreamConstants.START_ELEMENT && reader.localName == "attr")
            {
                val name = reader.getAttributeValue(null, "name") ?: ""
                if (name.isNotEmpty() && seen.add(name))
                {
                    entries += ParsedNsEntry.AttrDef(name, namespace, sourcePackage)
                }
            }
            else if (event == XMLStreamConstants.END_ELEMENT && reader.localName == "declare-styleable")
            {
                break
            }
        }
    }
}
