package dev.axp.layoutlib.worker.resources

import com.android.resources.ResourceType
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * W3-RESOURCE-VALUE-LOADER (08 §7.7.2): 단일 values XML (e.g. themes.xml) → `List<ParsedEntry>`.
 *
 * kxml2 기반 (Bridge 내부와 동일 parser) — 스타일 텍스트 정규화가 일관.
 * parser 는 refs (`@android:...`, `?attr:...`) 를 해석하지 않는다 — string 그대로 보관.
 *
 * 지원 tag: dimen / color / integer / bool / string / item / style / attr / declare-styleable.
 * skip (forward-compat): public, eat-comment, 기타 unknown.
 *
 * **attr 수집 규칙** (W3D1 pair-review F1): top-level `<attr>` 와 `<declare-styleable>` 내부의
 * `<attr>` 모두 `ParsedEntry.AttrDef` 로 수집. 실 `attrs.xml` 은 top-level 이 0 개이고 모두
 * declare-styleable 자식이므로 이 로직이 필수. 동일 name 이 복수 등장하면 파서는 모두 emit 하고
 * Loader/Bundle 단계에서 **first-wins** 로 dedupe.
 */
object FrameworkValueParser {

    fun parse(path: Path): List<ParsedEntry> =
        Files.newInputStream(path).use { parse(path.fileName.toString(), it) }

    fun parse(fileLabel: String, input: InputStream): List<ParsedEntry> {
        val parser = KXmlParser()
        try {
            parser.setInput(input, null)
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)

            val out = mutableListOf<ParsedEntry>()

            // <resources> 까지 skip.
            var event = parser.next()
            while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT) {
                event = parser.next()
            }
            if (event == XmlPullParser.END_DOCUMENT) return out
            if (parser.name != TAG_RESOURCES) {
                error("root element 는 <$TAG_RESOURCES> 여야 함: ${parser.name}")
            }

            // <resources> 의 direct children 순회.
            while (true) {
                event = parser.next()
                when (event) {
                    XmlPullParser.END_DOCUMENT -> break
                    XmlPullParser.END_TAG -> if (parser.name == TAG_RESOURCES) break
                    XmlPullParser.START_TAG -> handleTopLevelTag(parser, out)
                    else -> { /* text / CDATA 등 무시 */ }
                }
            }
            return out
        } catch (e: XmlPullParserException) {
            throw IllegalStateException("XML 파싱 실패 [$fileLabel]: ${e.message}", e)
        } catch (e: Throwable) {
            if (e is IllegalStateException && e.message?.contains(fileLabel) == true) throw e
            throw IllegalStateException("XML 파싱 실패 [$fileLabel]: ${e.message}", e)
        }
    }

    private fun handleTopLevelTag(p: KXmlParser, out: MutableList<ParsedEntry>) {
        when (val tag = p.name) {
            TAG_DIMEN -> out += simpleValue(p, ResourceType.DIMEN)
            TAG_COLOR -> out += simpleValue(p, ResourceType.COLOR)
            TAG_INTEGER -> out += simpleValue(p, ResourceType.INTEGER)
            TAG_BOOL -> out += simpleValue(p, ResourceType.BOOL)
            TAG_STRING -> out += simpleValue(p, ResourceType.STRING)
            TAG_ITEM -> {
                val typeAttr = p.getAttributeValue(null, ATTR_TYPE)
                val resType = typeAttr?.let { ResourceType.fromXmlValue(it) }
                val name = p.getAttributeValue(null, ATTR_NAME) ?: ""
                val value = readText(p)
                if (resType != null && name.isNotEmpty()) {
                    out += ParsedEntry.SimpleValue(resType, name, value)
                }
            }
            TAG_STYLE -> out += parseStyle(p)
            TAG_ATTR -> collectAttr(p, out)
            TAG_DECLARE_STYLEABLE -> parseDeclareStyleable(p, out)
            TAG_PUBLIC, TAG_EAT_COMMENT -> {
                skipTag(p)
            }
            else -> {
                System.err.println("[FrameworkValueParser] unknown top-level tag skipped: <$tag>")
                skipTag(p)
            }
        }
    }

    /**
     * 현재 `<attr>` START_TAG 에서 name/format 추출 후 AttrDef 를 out 에 추가. 그다음 tag 끝까지 소비.
     * top-level 과 nested (declare-styleable 자식) 모두에서 공유.
     */
    private fun collectAttr(p: KXmlParser, out: MutableList<ParsedEntry>) {
        val name = p.getAttributeValue(null, ATTR_NAME)
        val format = p.getAttributeValue(null, ATTR_FORMAT)
        if (!name.isNullOrEmpty()) {
            out += ParsedEntry.AttrDef(name, format)
        }
        skipTag(p)
    }

    /**
     * `<declare-styleable>` 자식 `<attr>` 를 모두 수집. styleable 자체는 resource 로 취급하지 않음.
     * W3D1 pair-review F1: 실 attrs.xml 은 top-level <attr> 이 0 개이고 모든 attr 이 여기서 수집됨.
     */
    private fun parseDeclareStyleable(p: KXmlParser, out: MutableList<ParsedEntry>) {
        while (true) {
            val event = p.next()
            when (event) {
                XmlPullParser.END_DOCUMENT -> return
                XmlPullParser.END_TAG -> if (p.name == TAG_DECLARE_STYLEABLE) return
                XmlPullParser.START_TAG -> if (p.name == TAG_ATTR) {
                    collectAttr(p, out)
                } else {
                    skipTag(p)
                }
                else -> { /* text / comment */ }
            }
        }
    }

    private fun simpleValue(p: KXmlParser, type: ResourceType): ParsedEntry.SimpleValue {
        val name = p.getAttributeValue(null, ATTR_NAME) ?: ""
        val value = readText(p)
        return ParsedEntry.SimpleValue(type, name, value)
    }

    private fun parseStyle(p: KXmlParser): ParsedEntry.StyleDef {
        val name = p.getAttributeValue(null, ATTR_NAME) ?: ""
        val parentAttr = p.getAttributeValue(null, ATTR_PARENT)
        val items = mutableListOf<StyleItem>()

        while (true) {
            val event = p.next()
            when (event) {
                XmlPullParser.END_DOCUMENT -> return ParsedEntry.StyleDef(name, parentAttr, items.toList())
                XmlPullParser.END_TAG -> if (p.name == TAG_STYLE) {
                    return ParsedEntry.StyleDef(name, parentAttr, items.toList())
                }
                XmlPullParser.START_TAG -> if (p.name == TAG_ITEM) {
                    val itemName = p.getAttributeValue(null, ATTR_NAME) ?: ""
                    val itemValue = readText(p)
                    if (itemName.isNotEmpty()) items += StyleItem(itemName, itemValue)
                } else {
                    skipTag(p)
                }
                else -> { /* text / comment */ }
            }
        }
    }

    /**
     * 현재 START_TAG → 매칭되는 END_TAG 까지 이벤트 skip. nested tag 포함.
     */
    private fun skipTag(p: KXmlParser) {
        if (p.eventType != XmlPullParser.START_TAG) return
        var depth = 1
        while (depth > 0) {
            when (p.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    /**
     * 현재 START_TAG 의 text 내용을 읽고 END_TAG 까지 진행.
     * 중첩 tag 있으면 (e.g. `<xliff:g>`) text 누적, 자식 tag 는 text 로만 취급.
     */
    private fun readText(p: KXmlParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (val event = p.next()) {
                XmlPullParser.TEXT -> sb.append(p.text)
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return sb.toString()
                XmlPullParser.CDSECT -> sb.append(p.text)
                else -> { /* ignore */ }
            }
        }
        return sb.toString()
    }

    // 태그/속성 이름 상수 — CLAUDE.md zero-magic-strings.
    private const val TAG_RESOURCES = "resources"
    private const val TAG_DIMEN = "dimen"
    private const val TAG_COLOR = "color"
    private const val TAG_INTEGER = "integer"
    private const val TAG_BOOL = "bool"
    private const val TAG_STRING = "string"
    private const val TAG_ITEM = "item"
    private const val TAG_STYLE = "style"
    private const val TAG_ATTR = "attr"
    private const val TAG_DECLARE_STYLEABLE = "declare-styleable"
    private const val TAG_PUBLIC = "public"
    private const val TAG_EAT_COMMENT = "eat-comment"

    private const val ATTR_NAME = "name"
    private const val ATTR_PARENT = "parent"
    private const val ATTR_TYPE = "type"
    private const val ATTR_FORMAT = "format"
}
