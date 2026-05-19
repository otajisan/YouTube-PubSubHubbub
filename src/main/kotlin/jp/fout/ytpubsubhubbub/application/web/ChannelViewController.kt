package jp.fout.ytpubsubhubbub.application.web

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jp.fout.ytpubsubhubbub.domain.notification.NotificationRepository
import jp.fout.ytpubsubhubbub.domain.subscription.SubscriptionService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping

@Controller
@Validated
class ChannelViewController(
    private val subscriptionService: SubscriptionService,
    private val notificationRepository: NotificationRepository,
) {
    @GetMapping("/")
    fun index(model: Model): String {
        val subs = subscriptionService.list().sortedByDescending { it.createdAt }
        val counts = subs.associate { it.channelId to notificationRepository.countByChannelId(it.channelId) }
        model.addAttribute("subscriptions", subs)
        model.addAttribute("notificationCounts", counts)
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", ChannelForm())
        }
        return "channels"
    }

    @PostMapping("/channels")
    fun create(
        @Valid @ModelAttribute("form") form: ChannelForm,
        bindingResult: BindingResult,
        model: Model,
    ): String {
        if (bindingResult.hasErrors()) {
            return index(model)
        }
        subscriptionService.register(form.channelId.trim())
        return "redirect:/"
    }

    @PostMapping("/channels/{id}/unsubscribe")
    fun delete(@PathVariable id: Long): String {
        subscriptionService.delete(id)
        return "redirect:/"
    }

    data class ChannelForm(
        @field:NotBlank
        @field:Pattern(
            regexp = "^UC[A-Za-z0-9_-]{22}$",
            message = "channelId must match YouTube channel ID format (UC + 22 chars)",
        )
        val channelId: String = "",
    )
}
