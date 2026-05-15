package jp.fout.ytpubsubhubbub.domain.subscription

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(
    name = "subscriptions",
    indexes = [
        Index(name = "ux_subscriptions_channel_id", columnList = "channel_id", unique = true),
        Index(name = "ux_subscriptions_callback_token", columnList = "callback_token", unique = true),
    ],
)
class Subscription(
    @Column(name = "channel_id", nullable = false)
    var channelId: String,

    @Column(name = "topic_url", nullable = false)
    var topicUrl: String,

    @Column(name = "callback_token", nullable = false)
    var callbackToken: String,

    @Column(name = "hub_secret", nullable = false)
    var hubSecret: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: SubscriptionStatus,

    @Column(name = "lease_seconds")
    var leaseSeconds: Long? = null,

    @Column(name = "subscribed_at")
    var subscribedAt: LocalDateTime? = null,

    @Column(name = "expires_at")
    var expiresAt: LocalDateTime? = null,

    @Column(name = "last_error", length = 1024)
    var lastError: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
