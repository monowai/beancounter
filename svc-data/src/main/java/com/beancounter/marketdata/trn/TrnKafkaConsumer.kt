package com.beancounter.marketdata.trn

import com.beancounter.client.ingest.FxTransactions
import com.beancounter.client.ingest.RowAdapter
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.Portfolio
import com.beancounter.common.utils.BcJson
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.service.FxRateService
import com.fasterxml.jackson.core.JsonProcessingException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import java.io.IOException
import javax.annotation.PostConstruct

@Service
@ConditionalOnProperty(value = ["kafka.enabled"], matchIfMissing = true)
class TrnKafkaConsumer {
    @Value("\${beancounter.topics.trn.csv:bc-trn-csv-dev}")
    var topicTrnCsv: String? = null

    @Value("\${beancounter.topics.trn.event:bc-trn-event-dev}")
    var topicTrnEvent: String? = null
    private lateinit var rowAdapter: RowAdapter
    private lateinit var fxTransactions: FxTransactions
    private lateinit var trnService: TrnService
    private lateinit var fxRateService: FxRateService
    private lateinit var portfolioService: PortfolioService
    private val om = BcJson.objectMapper

    @PostConstruct
    fun logSettings() {
        log.info("trn.csv {}, trn.event {}", topicTrnCsv, topicTrnEvent)
    }

    @Autowired
    fun setFxTransactions(fxTransactions: FxTransactions) {
        this.fxTransactions = fxTransactions
    }

    @Autowired
    fun setTrnService(trnService: TrnService) {
        this.trnService = trnService
    }

    @Autowired
    fun setFxRateService(fxRateService: FxRateService) {
        this.fxRateService = fxRateService
    }

    @Autowired
    fun setPortfolioService(portfolioService: PortfolioService) {
        this.portfolioService = portfolioService
    }

    @Autowired
    fun setRowAdapter(rowAdapter: RowAdapter) {
        this.rowAdapter = rowAdapter
    }

    @KafkaListener(topics = ["#{@trnCsvTopic}"], errorHandler = "bcErrorHandler")
    @Throws(IOException::class)
    fun fromCsvImport(payload: String?): TrnResponse {
        return fromCsvImport(om.readValue(payload, TrustedTrnImportRequest::class.java))!!
    }

    fun fromCsvImport(trustedRequest: TrustedTrnImportRequest): TrnResponse? {
        return if (trustedRequest.message != null) {
            log.info("Portfolio {} {}",
                    trustedRequest.portfolio.code,
                    trustedRequest.message)
            TrnResponse()
        } else {
            log.trace("Received Message {}", trustedRequest.toString())
            if (verifyPortfolio(trustedRequest.portfolio.id)) {
                val trnInput = rowAdapter.transform(trustedRequest)
                return writeTrn(trustedRequest.portfolio, trnInput)
            }
            null
        }
    }

    @KafkaListener(topics = ["#{@trnEventTopic}"], errorHandler = "bcErrorHandler")
    @Throws(JsonProcessingException::class)
    fun fromTrnRequest(payload: String?): TrnResponse {
        return fromTrnRequest(om.readValue(payload, TrustedTrnEvent::class.java))!!
    }

    fun fromTrnRequest(trustedTrnEvent: TrustedTrnEvent): TrnResponse? {
        log.trace("Received Message {}", trustedTrnEvent.toString())
        if (verifyPortfolio(trustedTrnEvent.portfolio.id)) {
            if (!trnService.isExists(trustedTrnEvent)) {
                return writeTrn(trustedTrnEvent.portfolio, trustedTrnEvent.trnInput)
            }
            run {
                log.debug(
                        "Ignoring transaction on {} that already exists",
                        trustedTrnEvent.trnInput.tradeDate)
            }
        }
        return null
    }

    private fun writeTrn(portfolio: Portfolio, trnInput: TrnInput): TrnResponse {
        val fxRequest = fxTransactions.buildRequest(portfolio, trnInput)
        val (data) = fxRateService.getRates(fxRequest)
        fxTransactions.setRates(data, fxRequest, trnInput)
        val trnRequest = TrnRequest(portfolio.id, listOf(trnInput))
        return trnService.save(portfolio, trnRequest)
    }

    private fun verifyPortfolio(portfolioId: String): Boolean {
        if (!portfolioService.verify(portfolioId)) {
            log.debug("Portfolio {} no longer exists. Ignoring", portfolioId)
            return false
        }
        return true
    }

    companion object {
        private val log = LoggerFactory.getLogger(TrnKafkaConsumer::class.java)
    }
}