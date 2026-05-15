package jp.fout.ytpubsubhubbub.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val callback: Callback,
    val hub: Hub,
) {
    data class Callback(val baseUrl: String)
    data class Hub(
        val url: String,
        val defaultLeaseSeconds: Long,
    )
}
