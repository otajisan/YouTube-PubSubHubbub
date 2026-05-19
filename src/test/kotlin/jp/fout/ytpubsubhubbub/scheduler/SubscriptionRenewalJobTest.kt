package jp.fout.ytpubsubhubbub.scheduler

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import jp.fout.ytpubsubhubbub.domain.subscription.Subscription
import jp.fout.ytpubsubhubbub.domain.subscription.SubscriptionRepository
import jp.fout.ytpubsubhubbub.domain.subscription.SubscriptionStatus
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class SubscriptionRenewalJobTest @Autowired constructor(
    private val job: SubscriptionRenewalJob,
    private val repository: SubscriptionRepository,
) {
    @Test
    fun `renews subscriptions expiring within one day`() {
        wireMock.resetAll()
        wireMock.stubFor(
            post(urlEqualTo("/subscribe"))
                .willReturn(aResponse().withStatus(202)),
        )

        val channelId = "UC" + (0..21).joinToString("") { "r" }
        val sub = repository.save(
            Subscription(
                channelId = channelId,
                topicUrl = "https://www.youtube.com/xml/feeds/videos.xml?channel_id=$channelId",
                callbackToken = UUID.randomUUID().toString(),
                hubSecret = "renewal-secret",
                status = SubscriptionStatus.ACTIVE,
                leaseSeconds = 864000,
                subscribedAt = LocalDateTime.now().minusDays(9),
                expiresAt = LocalDateTime.now().plusHours(12),
            ),
        )

        job.renewExpiringSubscriptions()

        val updated = repository.findById(sub.id!!).orElseThrow()
        assertEquals(SubscriptionStatus.PENDING, updated.status)
        val requests = wireMock.findAll(postRequestedFor(urlEqualTo("/subscribe")))
        assertEquals(1, requests.size)
        assertNotNull(requests[0].bodyAsString)
    }

    @Test
    fun `does not renew subscriptions far from expiry`() {
        wireMock.resetAll()
        wireMock.stubFor(
            post(urlEqualTo("/subscribe"))
                .willReturn(aResponse().withStatus(202)),
        )

        val channelId = "UC" + (0..21).joinToString("") { "s" }
        repository.save(
            Subscription(
                channelId = channelId,
                topicUrl = "https://www.youtube.com/xml/feeds/videos.xml?channel_id=$channelId",
                callbackToken = UUID.randomUUID().toString(),
                hubSecret = "no-renew",
                status = SubscriptionStatus.ACTIVE,
                leaseSeconds = 864000,
                subscribedAt = LocalDateTime.now(),
                expiresAt = LocalDateTime.now().plusDays(5),
            ),
        )

        job.renewExpiringSubscriptions()

        val requests = wireMock.findAll(postRequestedFor(urlEqualTo("/subscribe")))
        assertEquals(0, requests.size)
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
