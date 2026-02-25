package com.beancounter.position.service

import com.beancounter.common.model.Portfolio
import org.slf4j.LoggerFactory
import org.springframework.cloud.stream.function.StreamBridge
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Service

@Service
class MarketValueUpdateProducer(
    private val streamBridge: StreamBridge
) {
    private val log = LoggerFactory.getLogger(MarketValueUpdateProducer::class.java)

    fun sendMessage(payload: Portfolio) {
        try {
            val message =
                MessageBuilder
                    .withPayload(payload)
                    .setHeader(KafkaHeaders.KEY, payload.id)
                    .build()
            if (!streamBridge.send("portfolioMarketValue-out-0", message)) {
                log.error("StreamBridge returned false for portfolio market value update: {}", payload.code)
            }
        } catch (e: RuntimeException) {
            log.error("Failed to send market value update for portfolio {}: {}", payload.code, e.message)
        }
    }
}