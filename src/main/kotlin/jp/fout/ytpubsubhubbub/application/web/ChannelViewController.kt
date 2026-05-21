package jp.fout.ytpubsubhubbub.application.web

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jp.fout.ytpubsubhubbub.domain.notification.NotificationRepository
import jp.fout.ytpubsubhubbub.domain.subscription.BulkRegistrationResult
import jp.fout.ytpubsubhubbub.domain.subscription.SubscriptionService
import jp.fout.ytpubsubhubbub.domain.subscription.SubscriptionStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.support.RedirectAttributes

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
        val statusCounts = SubscriptionStatus.entries
            .associateWith { status -> subs.count { it.status == status } }
        model.addAttribute("subscriptions", subs)
        model.addAttribute("notificationCounts", counts)
        model.addAttribute("statusCounts", statusCounts)
        model.addAttribute("totalCount", subs.size)
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

    @PostMapping("/channels/bulk")
    fun bulkCreate(
        @RequestParam("channelIdsText", required = false) channelIdsText: String?,
        @RequestParam("csvFile", required = false) csvFile: MultipartFile?,
        redirectAttributes: RedirectAttributes,
    ): String {
        val inputs = parseBulkInputs(channelIdsText, csvFile)
        val result = subscriptionService.registerBulk(inputs)
        redirectAttributes.addFlashAttribute("bulkResult", result)
        return "redirect:/channels/bulk/result"
    }

    @GetMapping("/channels/bulk/result")
    fun bulkResult(model: Model): String {
        val bulkResult = model.asMap()["bulkResult"] as? BulkRegistrationResult
            ?: return "redirect:/"
        model.addAttribute("result", bulkResult)
        return "bulk-result"
    }

    @PostMapping("/channels/{id}/unsubscribe")
    fun delete(@PathVariable id: Long): String {
        subscriptionService.delete(id)
        return "redirect:/"
    }

    private fun parseBulkInputs(text: String?, file: MultipartFile?): List<String> {
        val tokens = mutableListOf<String>()
        text?.let { tokens += splitTokens(it) }
        if (file != null && !file.isEmpty) {
            file.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line -> tokens += splitTokens(line) }
            }
        }
        return tokens.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.lowercase() in CSV_HEADER_TOKENS }
            .toList()
    }

    private fun splitTokens(line: String): List<String> =
        line.split(',', '\n', '\r', '\t', ';', ' ')

    data class ChannelForm(
        @field:NotBlank
        @field:Pattern(
            regexp = "^UC[A-Za-z0-9_-]{22}$",
            message = "channelId must match YouTube channel ID format (UC + 22 chars)",
        )
        val channelId: String = "",
    )

    companion object {
        private val CSV_HEADER_TOKENS = setOf("channel_id", "channelid", "channel id", "id")
    }
}
