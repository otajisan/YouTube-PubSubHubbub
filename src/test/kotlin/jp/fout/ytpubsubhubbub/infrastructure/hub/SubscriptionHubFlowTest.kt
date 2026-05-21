package jp.fout.ytpubsubhubbub.infrastructure.hub

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import jp.fout.ytpubsubhubbub.domain.subscription.SubscriptionRepository
import jp.fout.ytpubsubhubbub.domain.subscription.SubscriptionService
import jp.fout.ytpubsubhubbub.domain.subscription.SubscriptionStatus
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SubscriptionHubFlowTest @Autowired constructor(
    private val service: SubscriptionService,
    private val repository: SubscriptionRepository,
) {
    @BeforeEach
    fun cleanDb() {
        repository.deleteAll()
    }

    @Test
    fun `register saves PENDING and posts subscribe form to hub`() {
        wireMock.resetAll()
        wireMock.stubFor(
            post(urlEqualTo("/subscribe"))
                .willReturn(aResponse().withStatus(202)),
        )

        val channelId = "UC" + (0..21).joinToString("") { "x" }

        val saved = service.register(channelId)
        assertNotNull(saved.id)
        assertEquals(SubscriptionStatus.PENDING, saved.status)
        assertNotNull(repository.findByChannelId(channelId))

        val requests = wireMock.findAll(postRequestedFor(urlEqualTo("/subscribe")))
        assertEquals(1, requests.size, "expected exactly one subscribe POST")

        val body = parseForm(requests[0].bodyAsString)
        assertEquals(
            "https://www.youtube.com/xml/feeds/videos.xml?channel_id=$channelId",
            body["hub.topic"],
        )
        assertEquals("async", body["hub.verify"])
        assertEquals("subscribe", body["hub.mode"])
        assertEquals("864000", body["hub.lease_seconds"])
        assertNotNull(body["hub.secret"])
        assertNotNull(body["hub.callback"])
        assertTrue(body["hub.callback"]!!.contains("/callback/"))
    }

    @Test
    fun `register marks FAILED when hub returns 4xx`() {
        wireMock.resetAll()
        wireMock.stubFor(
            post(urlEqualTo("/subscribe"))
                .willReturn(aResponse().withStatus(400).withBody("bad topic")),
        )

        val channelId = "UC" + (0..21).joinToString("") { "y" }
        val saved = service.register(channelId)

        val persisted = repository.findByChannelId(channelId)
        assertNotNull(persisted)
        assertEquals(SubscriptionStatus.FAILED, persisted.status)
        assertNotNull(persisted.lastError)
    }

    private fun parseForm(body: String): Map<String, String> =
        body.split("&").associate {
            val (k, v) = it.split("=", limit = 2)
            URLDecoder.decode(k, StandardCharsets.UTF_8) to URLDecoder.decode(v, StandardCharsets.UTF_8)
        }

    companion object {
        private val wireMock: WireMockServer = WireMockServer(wireMockConfig().dynamicPort()).also { it.start() }

        @AfterAll
        @JvmStatic
        fun stopWireMock() {
            wireMock.stop()
        }

        @DynamicPropertySource
        @JvmStatic
        fun overrideProperties(registry: DynamicPropertyRegistry) {
            registry.add("app.hub.url") { "http://localhost:${wireMock.port()}/subscribe" }
        }
    }
}
