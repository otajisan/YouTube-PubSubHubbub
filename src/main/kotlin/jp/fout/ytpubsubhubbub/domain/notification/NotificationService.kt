package jp.fout.ytpubsubhubbub.domain.notification

import jp.fout.ytpubsubhubbub.domain.subscription.Subscription
import jp.fout.ytpubsubhubbub.infrastructure.youtube.ParsedEntry
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional
class NotificationService(
    private val repository: NotificationRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun ingest(subscription: Subscription, rawXml: String, entries: List<ParsedEntry>): Int {
        var saved = 0
        for (entry in entries) {
            val videoId = entry.videoId
            if (videoId.isNullOrBlank()) continue

            if (repository.existsByVideoIdAndUpdatedAt(videoId, entry.updatedAt)) {
                continue
            }

            val notification = Notification(
                subscriptionId = subscription.id
                    ?: error("subscription.id must be persisted before ingest"),
                videoId = videoId,
                channelId = entry.channelId ?: subscription.channelId,
                title = entry.title,
                link = entry.link,
                publishedAt = entry.publishedAt,
                updatedAt = entry.updatedAt,
                receivedAt = LocalDateTime.now(),
                rawXml = rawXml,
            )
            repository.save(notification)
            saved++
        }
        if (saved > 0) {
            log.info("ingested {} notification(s) for channel {}", saved, subscription.channelId)
        }
        return saved
    }

    @Transactional(readOnly = true)
    fun list(pageable: Pageable, channelId: String? = null): Page<Notification> =
        if (channelId.isNullOrBlank()) repository.findAll(pageable)
        else repository.findAllByChannelId(channelId, pageable)
}
