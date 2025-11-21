package com.beancounter.position.service

import com.beancounter.common.model.Portfolio
import org.springframework.cloud.stream.function.StreamBridge
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Service

@Service
class MarketValueUpdateProducer(
    private val streamBridge: StreamBridge
) {
    fun sendMessage(payload: Portfolio) {
        val message =
            MessageBuilder
                .withPayload(payload)
                .setHeader(KafkaHeaders.KEY, payload.id)
                .build()
        streamBridge.send("portfolioMarketValue-out-0", message)
    }
}