package jp.fout.ytpubsubhubbub.application.web

import jp.fout.ytpubsubhubbub.domain.notification.NotificationService
import jp.fout.ytpubsubhubbub.domain.subscription.SubscriptionRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
class NotificationViewController(
    private val notificationService: NotificationService,
    private val subscriptionRepository: SubscriptionRepository,
) {
    @GetMapping("/notifications")
    fun list(
        @RequestParam(name = "channelId", required = false) channelId: String?,
        @RequestParam(name = "page", defaultValue = "0") page: Int,
        @RequestParam(name = "size", defaultValue = "50") size: Int,
        model: Model,
    ): String {
        val safeSize = size.coerceIn(MIN_SIZE, MAX_SIZE)
        val pageable = PageRequest.of(
            page.coerceAtLeast(0),
            safeSize,
            Sort.by(Sort.Direction.DESC, "receivedAt"),
        )
        val filter = channelId?.trim()?.takeIf { it.isNotEmpty() }
        val notifications = notificationService.list(pageable, filter)
        val channels = subscriptionRepository.findAll()
            .map { it.channelId }
            .sorted()

        model.addAttribute("notifications", notifications)
        model.addAttribute("channels", channels)
        model.addAttribute("filterChannelId", filter ?: "")
        return "notifications"
    }

    companion object {
        private const val MIN_SIZE = 10
        private const val MAX_SIZE = 200
    }
}
