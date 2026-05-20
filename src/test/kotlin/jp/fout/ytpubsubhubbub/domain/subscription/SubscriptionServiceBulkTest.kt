package jp.fout.ytpubsubhubbub.domain.subscription

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SubscriptionServiceBulkTest @Autowired constructor(
    private val service: SubscriptionService,
    private val repository: SubscriptionRepository,
) {
    @BeforeEach
    fun resetHubAndDb() {
        repository.deleteAll()
        wireMock.resetAll()
        wireMock.stubFor(
            post(urlEqualTo("/subscribe")).willReturn(aResponse().withStatus(202)),
        )
    }

    @Test
    fun `registerBulk classifies registered, skipped, invalid, and dedupes input`() {
        val valid1 = "UC" + "a".repeat(22)
        val valid2 = "UC" + "b".repeat(22)
        val existing = "UC" + "c".repeat(22)
        repository.save(
            Subscription(
                channelId = existing,
                topicUrl = "https://www.youtube.com/xml/feeds/videos.xml?channel_id=$existing",
                callbackToken = "token-existing",
                hubSecret = "secret",
                status = SubscriptionStatus.ACTIVE,
            ),
        )

        val inputs = listOf(
            valid1,
            "  $valid1  ",
            valid2,
            existing,
            "not-a-channel",
            "",
        )

        val result = service.registerBulk(inputs)

        assertEquals(listOf(valid1, valid2), result.registered)
        assertEquals(listOf(existing), result.skipped)
        assertEquals(1, result.invalid.size)
        assertEquals("not-a-channel", result.invalid[0].input)
        assertTrue(result.failed.isEmpty())
    }

    @Test
    fun `registerBulk continues past hub failures and marks per-channel failure`() {
        val ok = "UC" + "d".repeat(22)
        val bad = "UC" + "e".repeat(22)

        wireMock.resetAll()
        wireMock.stubFor(
            post(urlEqualTo("/subscribe"))
                .withRequestBody(containing("channel_id%3D$bad"))
                .willReturn(aResponse().withStatus(400).withBody("bad topic")),
        )
        wireMock.stubFor(
            post(urlEqualTo("/subscribe"))
                .withRequestBody(containing("channel_id%3D$ok"))
                .willReturn(aResponse().withStatus(202)),
        )

        val result = service.registerBulk(listOf(bad, ok))

        assertEquals(listOf(ok), result.registered)
        assertEquals(1, result.failed.size)
        assertEquals(bad, result.failed[0].input)
    }

    companion object {
        private val wireMock: WireMockServer =
            WireMockServer(wireMockConfig().dynamicPort()).also { it.start() }

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
