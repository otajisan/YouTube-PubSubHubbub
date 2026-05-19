package jp.fout.ytpubsubhubbub.domain.subscription

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface SubscriptionRepository : JpaRepository<Subscription, Long> {
    fun findByChannelId(channelId: String): Subscription?
    fun findByCallbackToken(callbackToken: String): Subscription?
    fun findAllByStatus(status: SubscriptionStatus): List<Subscription>
    fun findAllByStatusAndExpiresAtBefore(
        status: SubscriptionStatus,
        threshold: LocalDateTime,
    ): List<Subscription>
}
