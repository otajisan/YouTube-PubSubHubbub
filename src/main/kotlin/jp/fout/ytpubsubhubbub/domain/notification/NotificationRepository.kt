package jp.fout.ytpubsubhubbub.domain.notification

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface NotificationRepository : JpaRepository<Notification, Long> {
    fun findAllByChannelId(channelId: String, pageable: Pageable): Page<Notification>
    fun existsByVideoIdAndUpdatedAt(videoId: String, updatedAt: LocalDateTime?): Boolean
    fun countByChannelId(channelId: String): Long
}
