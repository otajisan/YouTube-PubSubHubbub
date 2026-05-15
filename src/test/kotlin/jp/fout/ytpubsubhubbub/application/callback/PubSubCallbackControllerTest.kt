package jp.fout.ytpubsubhubbub.application.callback

import jp.fout.ytpubsubhubbub.domain.notification.NotificationRepository
import jp.fout.ytpubsubhubbub.domain.subscription.Subscription
import jp.fout.ytpubsubhubbub.domain.subscription.SubscriptionRepository
import jp.fout.ytpubsubhubbub.domain.subscription.SubscriptionStatus
import jp.fout.ytpubsubhubbub.infrastructure.hub.HmacVerifier
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PubSubCallbackControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val subscriptionRepository: SubscriptionRepository,
    private val notificationRepository: NotificationRepository,
    private val hmacVerifier: HmacVerifier,
) {
    @Test
    fun `verify subscribe returns challenge and activates subscription`() {
        val token = UUID.randomUUID().toString()
        val channelId = "UC" + (0..21).joinToString("") { "a" }
        val topicUrl = "https://www.youtube.com/xml/feeds/videos.xml?channel_id=$channelId"
        subscriptionRepository.save(
            Subscription(
                channelId = channelId,
                topicUrl = topicUrl,
                callbackToken = token,
                hubSecret = "secret",
                status = SubscriptionStatus.PENDING,
            ),
        )

        mockMvc.perform(
            get("/callback/{token}", token)
                .param("hub.mode", "subscribe")
                .param("hub.topic", topicUrl)
                .param("hub.challenge", "abc-challenge-123")
                .param("hub.lease_seconds", "864000"),
        )
            .andExpect(status().isOk)
            .andExpect(content().string("abc-challenge-123"))

        val updated = subscriptionRepository.findByCallbackToken(token)
        assertNotNull(updated)
        assertEquals(SubscriptionStatus.ACTIVE, updated.status)
        assertEquals(864000L, updated.leaseSeconds)
        assertNotNull(updated.expiresAt)
    }

    @Test
    fun `verify rejects unknown token with 404`() {
        mockMvc.perform(
            get("/callback/{token}", UUID.randomUUID().toString())
                .param("hub.mode", "subscribe")
                .param("hub.topic", "https://www.youtube.com/xml/feeds/videos.xml?channel_id=UCxxxxxxxxxxxxxxxxxxxxxx")
                .param("hub.challenge", "any")
                .param("hub.lease_seconds", "864000"),
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `verify rejects topic mismatch with 404`() {
        val token = UUID.randomUUID().toString()
        val channelId = "UC" + (0..21).joinToString("") { "b" }
        subscriptionRepository.save(
            Subscription(
                channelId = channelId,
                topicUrl = "https://www.youtube.com/xml/feeds/videos.xml?channel_id=$channelId",
                callbackToken = token,
                hubSecret = "secret",
                status = SubscriptionStatus.PENDING,
            ),
        )

        mockMvc.perform(
            get("/callback/{token}", token)
                .param("hub.mode", "subscribe")
                .param("hub.topic", "https://www.youtube.com/xml/feeds/videos.xml?channel_id=UCotherchannelvalueonly")
                .param("hub.challenge", "abc")
                .param("hub.lease_seconds", "864000"),
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `receive valid signed atom persists notification`() {
        val token = UUID.randomUUID().toString()
        val channelId = "UCxxxxxxxxxxxxxxxxxxxxxx"
        val secret = "topsecret-${UUID.randomUUID()}"
        val saved = subscriptionRepository.save(
            Subscription(
                channelId = channelId,
                topicUrl = "https://www.youtube.com/xml/feeds/videos.xml?channel_id=$channelId",
                callbackToken = token,
                hubSecret = secret,
                status = SubscriptionStatus.ACTIVE,
            ),
        )
        val countBefore = notificationRepository.countByChannelId(channelId)

        val xml = loadFixture("/atom/sample-feed.xml")
        val bodyBytes = xml.toByteArray(StandardCharsets.UTF_8)
        val signature = hmacVerifier.sign(bodyBytes, secret, "sha1")

        mockMvc.perform(
            post("/callback/{token}", token)
                .header("X-Hub-Signature", signature)
                .contentType(MediaType.APPLICATION_ATOM_XML)
                .content(bodyBytes),
        )
            .andExpect(status().isAccepted)

        val countAfter = notificationRepository.countByChannelId(channelId)
        assertEquals(countBefore + 1, countAfter)

        val page = notificationRepository.findAllByChannelId(channelId, PageRequest.of(0, 10))
        val n = page.content.first { it.subscriptionId == saved.id }
        assertEquals("VIDEO_ID_AAA", n.videoId)
        assertEquals("Sample Video Title", n.title)
        assertEquals("https://www.youtube.com/watch?v=VIDEO_ID_AAA", n.link)
        assertNotNull(n.publishedAt)
        assertNotNull(n.updatedAt)
        assertTrue(n.rawXml!!.contains("VIDEO_ID_AAA"))
    }

    @Test
    fun `receive rejects bad signature without persisting`() {
        val token = UUID.randomUUID().toString()
        val channelId = "UCxxxxxxxxxxxxxxxxxxxxxx"
        subscriptionRepository.save(
            Subscription(
                channelId = channelId,
                topicUrl = "https://www.youtube.com/xml/feeds/videos.xml?channel_id=$channelId",
                callbackToken = token,
                hubSecret = "real-secret",
                status = SubscriptionStatus.ACTIVE,
            ),
        )
        val countBefore = notificationRepository.countByChannelId(channelId)
        val xml = loadFixture("/atom/sample-feed.xml")

        mockMvc.perform(
            post("/callback/{token}", token)
                .header("X-Hub-Signature", "sha1=deadbeef")
                .contentType(MediaType.APPLICATION_ATOM_XML)
                .content(xml.toByteArray(StandardCharsets.UTF_8)),
        )
            .andExpect(status().isAccepted)

        val countAfter = notificationRepository.countByChannelId(channelId)
        assertEquals(countBefore, countAfter)
    }

    @Test
    fun `receive unknown token returns 404`() {
        mockMvc.perform(
            post("/callback/{token}", UUID.randomUUID().toString())
                .header("X-Hub-Signature", "sha1=00")
                .contentType(MediaType.APPLICATION_ATOM_XML)
                .content("<feed/>".toByteArray(StandardCharsets.UTF_8)),
        )
            .andExpect(status().isNotFound)
    }

    private fun loadFixture(path: String): String =
        javaClass.getResourceAsStream(path)!!.use { it.readBytes().toString(Charsets.UTF_8) }
}
