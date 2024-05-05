package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.fasterxml.jackson.core.JsonProcessingException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.DependsOn
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional

/**
 * Write prices in relation to incoming messages.
 */
@Controller
@ConditionalOnProperty(value = ["kafka.enabled"], matchIfMissing = true)
@Transactional
@DependsOn("kafkaConfig")
class PriceWriter {
    private var priceService: PriceService? = null

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
        return priceService!!.handle(priceResponse)
    }

    companion object {
        private val log = LoggerFactory.getLogger(PriceWriter::class.java)
    }
}
