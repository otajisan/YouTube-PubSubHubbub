package jp.fout.ytpubsubhubbub

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
class YtPubsubhubbubApplication

fun main(args: Array<String>) {
    runApplication<YtPubsubhubbubApplication>(*args)
}
