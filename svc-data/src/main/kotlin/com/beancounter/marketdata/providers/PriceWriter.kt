package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.BcJson
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.DependsOn
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Write prices in relation to incoming messages.
 */
@Service
@ConditionalOnProperty(value = ["kafka.enabled"], matchIfMissing = true)
@Transactional
@DependsOn("kafkaConfig")
class PriceWriter {
    private var priceService: PriceService? = null
    private val objectMapper: ObjectMapper = BcJson().objectMapper

    @Autowired
    fun setPriceService(priceService: PriceService?) {
        this.priceService = priceService
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
