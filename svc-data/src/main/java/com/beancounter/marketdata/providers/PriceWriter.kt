package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.BcJson.objectMapper
import com.fasterxml.jackson.core.JsonProcessingException
import org.apache.kafka.clients.admin.NewTopic
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@ConditionalOnProperty(value = ["kafka.enabled"], matchIfMissing = true)
@Transactional
class PriceWriter {
    @Value("\${beancounter.topics.price:bc-price-dev}")
    var topicPrice: String? = null
    private var priceService: PriceService? = null

    @Autowired
    fun setPriceService(priceService: PriceService?) {
        this.priceService = priceService
    }

    @Bean
    fun topicPrice(): NewTopic {
        return NewTopic(topicPrice, 1, 1.toShort())
    }

    @Bean
    fun priceTopic(): String? {
        log.info("Topic: TRN-CSV set to {}", topicPrice)
        return topicPrice
    }

    @KafkaListener(topics = ["#{@priceTopic}"], errorHandler = "bcErrorHandler")
    @Throws(JsonProcessingException::class)
    fun processMessage(message: String?): Iterable<MarketData?>? {
        val priceResponse = objectMapper.readValue(message, PriceResponse::class.java)
        return processMessage(priceResponse)
    }

    fun processMessage(priceResponse: PriceResponse): Iterable<MarketData?>? {
        log.trace("Received Message {}", priceResponse.toString())
        return priceService!!.process(priceResponse)
    }

    companion object {
        private val log = LoggerFactory.getLogger(PriceWriter::class.java)
    }
}