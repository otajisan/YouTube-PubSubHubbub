package jp.fout.ytpubsubhubbub.domain.notification

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Lob
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "notifications",
    uniqueConstraints = [
        UniqueConstraint(
            name = "ux_notifications_video_updated",
            columnNames = ["video_id", "updated_at"],
        ),
    ],
    indexes = [
        Index(name = "ix_notifications_received_at", columnList = "received_at"),
        Index(name = "ix_notifications_channel_id", columnList = "channel_id"),
    ],
)
class Notification(
    @Column(name = "subscription_id", nullable = false)
    var subscriptionId: Long,

    @Column(name = "video_id", nullable = false)
    var videoId: String,

    @Column(name = "channel_id", nullable = false)
    var channelId: String,

    @Column(name = "title", length = 512)
    var title: String? = null,

    @Column(name = "link", length = 512)
    var link: String? = null,

    @Column(name = "published_at")
    var publishedAt: LocalDateTime? = null,

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null,

    @Column(name = "received_at", nullable = false)
    var receivedAt: LocalDateTime = LocalDateTime.now(),

    @Lob
    @Column(name = "raw_xml")
    var rawXml: String? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
