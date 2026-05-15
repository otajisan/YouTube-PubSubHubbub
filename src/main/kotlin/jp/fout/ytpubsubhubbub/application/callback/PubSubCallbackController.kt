package jp.fout.ytpubsubhubbub.application.callback

import jp.fout.ytpubsubhubbub.domain.notification.NotificationService
import jp.fout.ytpubsubhubbub.domain.subscription.SubscriptionService
import jp.fout.ytpubsubhubbub.domain.subscription.SubscriptionStatus
import jp.fout.ytpubsubhubbub.infrastructure.hub.HmacVerifier
import jp.fout.ytpubsubhubbub.infrastructure.youtube.AtomFeedParser
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import java.nio.charset.StandardCharsets

@Controller
class PubSubCallbackController(
    private val subscriptionService: SubscriptionService,
    private val notificationService: NotificationService,
    private val hmacVerifier: HmacVerifier,
    private val atomFeedParser: AtomFeedParser,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/callback/{token}", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun verify(
        @PathVariable token: String,
        @RequestParam(name = "hub.mode") mode: String,
        @RequestParam(name = "hub.topic") topic: String,
        @RequestParam(name = "hub.challenge") challenge: String,
        @RequestParam(name = "hub.lease_seconds", required = false) leaseSeconds: Long?,
        @RequestParam(name = "hub.reason", required = false) reason: String?,
    ): ResponseEntity<String> {
        val sub = subscriptionService.findByCallbackToken(token)
        if (sub == null) {
            log.warn("verification rejected: unknown token {}", token)
            return ResponseEntity.notFound().build()
        }
        if (sub.topicUrl != topic) {
            log.warn("verification rejected: topic mismatch for token {}", token)
            return ResponseEntity.notFound().build()
        }

        when (mode) {
            "subscribe" -> {
                if (leaseSeconds == null || leaseSeconds <= 0) {
                    log.warn("verification rejected: invalid leaseSeconds={} for token {}", leaseSeconds, token)
                    return ResponseEntity.notFound().build()
                }
                subscriptionService.markActive(sub, leaseSeconds)
                log.info("subscription ACTIVE channel={} leaseSeconds={}", sub.channelId, leaseSeconds)
            }

            "unsubscribe" -> {
                sub.status = SubscriptionStatus.UNSUBSCRIBED
                subscriptionService.markFailed(sub, "unsubscribed by hub")
                log.info("subscription UNSUBSCRIBED channel={}", sub.channelId)
            }

            "denied" -> {
                subscriptionService.markFailed(sub, "hub denied: ${reason ?: "unknown"}")
                log.warn("subscription DENIED channel={} reason={}", sub.channelId, reason)
                return ResponseEntity.ok("")
            }

            else -> {
                log.warn("unknown hub.mode={} for token {}", mode, token)
                return ResponseEntity.badRequest().build()
            }
        }

        return ResponseEntity.ok(challenge)
    }

    @PostMapping("/callback/{token}")
    fun receive(
        @PathVariable token: String,
        @RequestHeader(name = "X-Hub-Signature", required = false) signature: String?,
        @RequestBody body: ByteArray,
    ): ResponseEntity<Unit> {
        val sub = subscriptionService.findByCallbackToken(token)
        if (sub == null) {
            log.warn("notification rejected: unknown token {}", token)
            return ResponseEntity.notFound().build()
        }

        if (!hmacVerifier.verify(body, sub.hubSecret, signature)) {
            log.warn(
                "HMAC verification failed for channel {}: signatureHeader={}",
                sub.channelId,
                signature,
            )
            return ResponseEntity.accepted().build()
        }

        val xml = String(body, StandardCharsets.UTF_8)
        val entries = try {
            atomFeedParser.parse(body)
        } catch (e: Exception) {
            log.warn("failed to parse atom payload for channel {}: {}", sub.channelId, e.message)
            return ResponseEntity.accepted().build()
        }

        if (entries.isEmpty()) {
            log.info("no entries in atom payload for channel {}", sub.channelId)
            return ResponseEntity.accepted().build()
        }

        notificationService.ingest(sub, xml, entries)
        return ResponseEntity.accepted().build()
    }
}
