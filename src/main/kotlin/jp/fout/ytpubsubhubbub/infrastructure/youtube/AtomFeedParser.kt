package jp.fout.ytpubsubhubbub.infrastructure.youtube

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

data class ParsedEntry(
    val videoId: String?,
    val channelId: String?,
    val title: String?,
    val link: String?,
    val publishedAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)

@Component
class AtomFeedParser {
    private val log = LoggerFactory.getLogger(javaClass)

    private val documentBuilderFactory: DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
            isNamespaceAware = true
        }

    fun parse(xml: String): List<ParsedEntry> = parse(xml.toByteArray(StandardCharsets.UTF_8))

    fun parse(bytes: ByteArray): List<ParsedEntry> {
        val builder = documentBuilderFactory.newDocumentBuilder()
        val doc = ByteArrayInputStream(bytes).use { builder.parse(it) }
        doc.documentElement.normalize()

        val entries = doc.getElementsByTagNameNS(ATOM_NS, "entry")
        if (entries.length == 0) return emptyList()

        return (0 until entries.length).mapNotNull { idx ->
            val entry = entries.item(idx) as? Element ?: return@mapNotNull null
            ParsedEntry(
                videoId = childText(entry, YT_NS, "videoId"),
                channelId = childText(entry, YT_NS, "channelId"),
                title = childText(entry, ATOM_NS, "title"),
                link = findAlternateLink(entry),
                publishedAt = parseDate(childText(entry, ATOM_NS, "published")),
                updatedAt = parseDate(childText(entry, ATOM_NS, "updated")),
            )
        }
    }

    private fun childText(parent: Element, ns: String, localName: String): String? {
        val nodes = parent.getElementsByTagNameNS(ns, localName)
        if (nodes.length == 0) return null
        return nodes.item(0).textContent?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun findAlternateLink(entry: Element): String? {
        val links = entry.getElementsByTagNameNS(ATOM_NS, "link")
        for (i in 0 until links.length) {
            val node: Node = links.item(i)
            val el = node as? Element ?: continue
            val rel = el.getAttribute("rel")
            if (rel.isEmpty() || rel == "alternate") {
                return el.getAttribute("href").takeIf { it.isNotEmpty() }
            }
        }
        return null
    }

    private fun parseDate(value: String?): LocalDateTime? {
        if (value.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()
        } catch (e: Exception) {
            log.debug("could not parse date '{}': {}", value, e.message)
            null
        }
    }

    companion object {
        private const val ATOM_NS = "http://www.w3.org/2005/Atom"
        private const val YT_NS = "http://www.youtube.com/xml/schemas/2015"
    }
}
