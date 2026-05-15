package jp.fout.ytpubsubhubbub.domain

import jp.fout.ytpubsubhubbub.domain.subscription.Subscription
import jp.fout.ytpubsubhubbub.domain.subscription.SubscriptionRepository
import jp.fout.ytpubsubhubbub.domain.subscription.SubscriptionStatus
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@Transactional
class SubscriptionRepositoryTest @Autowired constructor(
    private val repository: SubscriptionRepository,
) {
    @Test
    fun `persists and retrieves a subscription by channelId`() {
        val channelId = "UC_TEST_${UUID.randomUUID()}"
        val sub = Subscription(
            channelId = channelId,
            topicUrl = "https://www.youtube.com/xml/feeds/videos.xml?channel_id=$channelId",
            callbackToken = UUID.randomUUID().toString(),
            hubSecret = "secret-${UUID.randomUUID()}",
            status = SubscriptionStatus.PENDING,
        )

        val saved = repository.save(sub)
        assertNotNull(saved.id)

        val fetched = repository.findByChannelId(channelId)
        assertNotNull(fetched)
        assertEquals(SubscriptionStatus.PENDING, fetched.status)
    }

    @Test
    fun `finds active subscriptions expiring before threshold`() {
        val channelId = "UC_TEST_${UUID.randomUUID()}"
        val expiringSoon = Subscription(
            channelId = channelId,
            topicUrl = "https://www.youtube.com/xml/feeds/videos.xml?channel_id=$channelId",
            callbackToken = UUID.randomUUID().toString(),
            hubSecret = "secret",
            status = SubscriptionStatus.ACTIVE,
            expiresAt = LocalDateTime.now().plusHours(12),
        )
        repository.save(expiringSoon)

        val threshold = LocalDateTime.now().plusDays(1)
        val results = repository.findAllByStatusAndExpiresAtBefore(SubscriptionStatus.ACTIVE, threshold)

        assertTrue(results.any { it.channelId == channelId })
    }
}
