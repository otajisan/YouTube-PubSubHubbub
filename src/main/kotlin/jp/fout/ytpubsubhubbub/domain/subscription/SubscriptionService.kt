package jp.fout.ytpubsubhubbub.domain.subscription

import jp.fout.ytpubsubhubbub.config.AppProperties
import jp.fout.ytpubsubhubbub.infrastructure.hub.HubException
import jp.fout.ytpubsubhubbub.infrastructure.hub.PubSubHubbubClient
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.HexFormat
import java.util.UUID

@Service
@Transactional
class SubscriptionService(
    private val repository: SubscriptionRepository,
    private val appProperties: AppProperties,
    private val hubClient: PubSubHubbubClient,
    @Lazy private val self: SubscriptionService,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun registerBulk(rawInputs: List<String>): BulkRegistrationResult {
        val registered = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val invalid = mutableListOf<BulkRegistrationFailure>()
        val failed = mutableListOf<BulkRegistrationFailure>()
        val seen = mutableSetOf<String>()

        for (raw in rawInputs) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue
            if (!seen.add(trimmed)) continue

            if (!CHANNEL_ID_REGEX.matches(trimmed)) {
                invalid.add(BulkRegistrationFailure(trimmed, "invalid channel id format"))
                continue
            }
            if (repository.findByChannelId(trimmed) != null) {
                skipped.add(trimmed)
                continue
            }

            val saved = register(trimmed)
            if (saved.status == SubscriptionStatus.FAILED) {
                failed.add(BulkRegistrationFailure(trimmed, saved.lastError ?: "unknown error"))
            } else {
                registered.add(trimmed)
            }
        }

        return BulkRegistrationResult(
            registered = registered.toList(),
            skipped = skipped.toList(),
            invalid = invalid.toList(),
            failed = failed.toList(),
        )
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun register(channelId: String): Subscription {
        // Commit the row in its own transaction first so the Hub's verification
        // GET against /callback/{token} can find the subscription. Otherwise the
        // outer transaction (especially in bulk) holds the INSERT uncommitted
        // until the loop finishes, and every verification is rejected as
        // "unknown token".
        val saved = self.persistPending(channelId)
        return try {
            hubClient.subscribe(
                topicUrl = saved.topicUrl,
                callbackUrl = callbackUrlFor(saved.callbackToken),
                hubSecret = saved.hubSecret,
                leaseSeconds = appProperties.hub.defaultLeaseSeconds,
            )
            saved
        } catch (e: HubException) {
            log.warn("subscribe failed for channel {}: {}", channelId, e.message)
            self.markFailedInNewTx(saved.id!!, e.message ?: "unknown")
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun persistPending(channelId: String): Subscription {
        val existing = repository.findByChannelId(channelId)
        val sub = existing ?: Subscription(
            channelId = channelId,
            topicUrl = topicUrlFor(channelId),
            callbackToken = UUID.randomUUID().toString(),
            hubSecret = randomSecret(),
            status = SubscriptionStatus.PENDING,
        )
        sub.status = SubscriptionStatus.PENDING
        sub.updatedAt = LocalDateTime.now()
        return repository.save(sub)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markFailedInNewTx(id: Long, reason: String): Subscription {
        val sub = repository.findById(id)
            .orElseThrow { IllegalStateException("subscription $id not found") }
        sub.status = SubscriptionStatus.FAILED
        sub.lastError = reason.take(1024)
        sub.updatedAt = LocalDateTime.now()
        return repository.save(sub)
    }

    @Transactional(readOnly = true)
    fun list(): List<Subscription> = repository.findAll()

    @Transactional(readOnly = true)
    fun findByCallbackToken(token: String): Subscription? =
        repository.findByCallbackToken(token)

    fun delete(id: Long) {
        val sub = repository.findById(id).orElse(null) ?: return
        try {
            hubClient.unsubscribe(
                topicUrl = sub.topicUrl,
                callbackUrl = callbackUrlFor(sub.callbackToken),
                hubSecret = sub.hubSecret,
            )
        } catch (e: HubException) {
            log.warn("unsubscribe failed for channel {}: {}", sub.channelId, e.message)
        }
        repository.delete(sub)
    }

    fun markActive(sub: Subscription, leaseSeconds: Long) {
        sub.status = SubscriptionStatus.ACTIVE
        sub.leaseSeconds = leaseSeconds
        sub.subscribedAt = LocalDateTime.now()
        sub.expiresAt = LocalDateTime.now().plusSeconds(leaseSeconds)
        sub.updatedAt = LocalDateTime.now()
        sub.lastError = null
        repository.save(sub)
    }

    fun markFailed(sub: Subscription, reason: String) {
        sub.status = SubscriptionStatus.FAILED
        sub.lastError = reason.take(1024)
        sub.updatedAt = LocalDateTime.now()
        repository.save(sub)
    }

    fun renew(sub: Subscription) {
        try {
            hubClient.subscribe(
                topicUrl = sub.topicUrl,
                callbackUrl = callbackUrlFor(sub.callbackToken),
                hubSecret = sub.hubSecret,
                leaseSeconds = appProperties.hub.defaultLeaseSeconds,
            )
            sub.status = SubscriptionStatus.PENDING
            sub.updatedAt = LocalDateTime.now()
            repository.save(sub)
        } catch (e: HubException) {
            log.warn("renew failed for channel {}: {}", sub.channelId, e.message)
            markFailed(sub, e.message ?: "unknown")
        }
    }

    fun callbackUrlFor(token: String): String =
        "${appProperties.callback.baseUrl.trimEnd('/')}/callback/$token"

    private fun topicUrlFor(channelId: String): String =
        "https://www.youtube.com/xml/feeds/videos.xml?channel_id=$channelId"

    private fun randomSecret(): String {
        val bytes = ByteArray(32).also { random.nextBytes(it) }
        return HexFormat.of().formatHex(bytes)
    }

    companion object {
        val CHANNEL_ID_REGEX = Regex("^UC[A-Za-z0-9_-]{22}$")
    }
}

data class BulkRegistrationFailure(
    val input: String,
    val reason: String,
)

data class BulkRegistrationResult(
    val registered: List<String>,
    val skipped: List<String>,
    val invalid: List<BulkRegistrationFailure>,
    val failed: List<BulkRegistrationFailure>,
) {
    val totalProcessed: Int
        get() = registered.size + skipped.size + invalid.size + failed.size
}
