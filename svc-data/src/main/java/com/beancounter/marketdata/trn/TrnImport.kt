package com.beancounter.marketdata.trn

import com.beancounter.client.ingest.FxTransactions
import com.beancounter.client.ingest.RowAdapter
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.Portfolio
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.service.FxRateService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class TrnImport {
    private lateinit var rowAdapter: RowAdapter
    private lateinit var portfolioService: PortfolioService
    private lateinit var fxRateService: FxRateService
    private lateinit var trnService: TrnService
    private lateinit var fxTransactions: FxTransactions

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
                if (trnInput != null) {
                    return writeTrn(trustedRequest.portfolio, trnInput)
                }
            }
            null
        }
    }

    fun fromTrnRequest(trustedTrnEvent: TrustedTrnEvent): TrnResponse? {
        log.trace("Received Message {}", trustedTrnEvent.toString())
        if (verifyPortfolio(trustedTrnEvent.portfolio.id)) {
            val existing = trnService.existing(trustedTrnEvent)
            if (existing.isEmpty()) {
                return writeTrn(trustedTrnEvent.portfolio, trustedTrnEvent.trnInput)
            }
            run {
                log.debug(
                        "Ignoring transaction on {} that already exists",
                        trustedTrnEvent.trnInput.tradeDate)
            }
        }
        return TrnResponse()
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
        private val log = LoggerFactory.getLogger(TrnImport::class.java)
    }

}