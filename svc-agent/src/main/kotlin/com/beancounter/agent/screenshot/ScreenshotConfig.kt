package com.beancounter.agent.screenshot

import com.beancounter.agent.AgentProvider
import com.beancounter.agent.DeepSeekConnection
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

/** Wires the screenshot reader to the DeepSeek endpoint the agent already uses. */
@Configuration
@ConditionalOnProperty(name = [AgentProvider.PROPERTY], havingValue = AgentProvider.DEEPSEEK, matchIfMissing = true)
class ScreenshotConfig {
    @Bean
    fun screenshotReader(connection: DeepSeekConnection): ScreenshotReader {
        val factory =
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(CONNECT_TIMEOUT)
                setReadTimeout(READ_TIMEOUT)
            }
        val http =
            RestClient
                .builder()
                .baseUrl(connection.baseUrl)
                .requestFactory(factory)
                .build()
        return ScreenshotReader(DeepSeekVision(http, connection.apiKey, connection.model))
    }

    private companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)
        val READ_TIMEOUT: Duration = Duration.ofSeconds(90)
    }
}