package jp.fout.ytpubsubhubbub.scheduler

import jp.fout.ytpubsubhubbub.domain.subscription.SubscriptionRepository
import jp.fout.ytpubsubhubbub.domain.subscription.SubscriptionService
import jp.fout.ytpubsubhubbub.domain.subscription.SubscriptionStatus
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime

@Component
class SubscriptionRenewalJob(
    private val repository: SubscriptionRepository,
    private val subscriptionService: SubscriptionService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1H")
    fun renewExpiringSubscriptions() {
        val threshold = LocalDateTime.now().plus(RENEW_BEFORE)
        val candidates = repository.findAllByStatusAndExpiresAtBefore(
            SubscriptionStatus.ACTIVE,
            threshold,
        )
        if (candidates.isEmpty()) return

        log.info("renewing {} subscription(s) expiring before {}", candidates.size, threshold)
        for (sub in candidates) {
            subscriptionService.renew(sub)
        }
    }

    companion object {
        private val RENEW_BEFORE: Duration = Duration.ofDays(1)
    }
}
