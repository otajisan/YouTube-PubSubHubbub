package jp.fout.ytpubsubhubbub.infrastructure.hub

import jp.fout.ytpubsubhubbub.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

@Component
class PubSubHubbubClient(
    private val appProperties: AppProperties,
    builder: RestClient.Builder,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val client: RestClient = builder.build()

    fun subscribe(
        topicUrl: String,
        callbackUrl: String,
        hubSecret: String,
        leaseSeconds: Long,
    ) {
        sendHubRequest(
            mode = "subscribe",
            topicUrl = topicUrl,
            callbackUrl = callbackUrl,
            hubSecret = hubSecret,
            leaseSeconds = leaseSeconds,
        )
    }

    fun unsubscribe(
        topicUrl: String,
        callbackUrl: String,
        hubSecret: String,
    ) {
        sendHubRequest(
            mode = "unsubscribe",
            topicUrl = topicUrl,
            callbackUrl = callbackUrl,
            hubSecret = hubSecret,
            leaseSeconds = null,
        )
    }

    private fun sendHubRequest(
        mode: String,
        topicUrl: String,
        callbackUrl: String,
        hubSecret: String,
        leaseSeconds: Long?,
    ) {
        val body = LinkedMultiValueMap<String, String>().apply {
            add("hub.callback", callbackUrl)
            add("hub.topic", topicUrl)
            add("hub.verify", "async")
            add("hub.mode", mode)
            leaseSeconds?.let { add("hub.lease_seconds", it.toString()) }
            add("hub.secret", hubSecret)
        }

        try {
            val response = client.post()
                .uri(appProperties.hub.url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .toBodilessEntity()

            log.info(
                "Hub {} request sent: topic={} callback={} status={}",
                mode,
                topicUrl,
                callbackUrl,
                response.statusCode,
            )
        } catch (e: RestClientResponseException) {
            throw HubException(
                "Hub returned ${e.statusCode}: ${e.responseBodyAsString.take(256)}",
                e,
            )
        } catch (e: Exception) {
            throw HubException("Hub call failed: ${e.message}", e)
        }
    }
}

class HubException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
