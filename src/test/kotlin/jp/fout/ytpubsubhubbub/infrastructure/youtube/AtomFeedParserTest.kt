package jp.fout.ytpubsubhubbub.infrastructure.youtube

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AtomFeedParserTest {
    private val parser = AtomFeedParser()

    @Test
    fun `parses a single-entry YouTube atom feed`() {
        val xml = loadResource("/atom/sample-feed.xml")
        val entries = parser.parse(xml)

        assertEquals(1, entries.size)
        val e = entries[0]
        assertEquals("VIDEO_ID_AAA", e.videoId)
        assertEquals("UCxxxxxxxxxxxxxxxxxxxxxx", e.channelId)
        assertEquals("Sample Video Title", e.title)
        assertEquals("https://www.youtube.com/watch?v=VIDEO_ID_AAA", e.link)
        assertEquals(LocalDateTime.parse("2026-05-13T07:30:00"), e.publishedAt)
        assertEquals(LocalDateTime.parse("2026-05-13T08:00:00"), e.updatedAt)
    }

    @Test
    fun `returns empty list for feed without entries`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Empty</title>
            </feed>
        """.trimIndent()

        assertEquals(0, parser.parse(xml).size)
    }

    private fun loadResource(path: String): String {
        val stream = javaClass.getResourceAsStream(path)
        assertNotNull(stream, "resource $path not found")
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
