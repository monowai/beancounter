package com.beancounter.position.service

import com.beancounter.common.model.Portfolio
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class MarketValueUpdateProducer(
    private val kafkaTemplate: KafkaTemplate<String, Portfolio>,
    @Value("\${beancounter.topics.pos.mv:bc-pos-mv-dev}") val topicPosMvName: String
) {
    fun sendMessage(payload: Portfolio) {
        kafkaTemplate.send(
            topicPosMvName,
            payload.id,
            payload
        )
    }
}