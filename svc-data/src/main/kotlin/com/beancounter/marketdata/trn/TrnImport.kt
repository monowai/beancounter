package com.beancounter.marketdata.trn

import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.Portfolio
import com.beancounter.marketdata.fx.FxRateService
import com.beancounter.marketdata.portfolio.PortfolioService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
/**
 * Controls the ingestion of transaction into the BC format by acting on a TrustedTrnRequest.
 * Verifies the requested portfolio can be accessed and backfills missing FX Rates.
 *
 * Transformations are delegated to the appropriate TrnAdapter
 */
class TrnImport {
    private lateinit var adapterFactory: AdapterFactory
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
    fun setAdapterFactory(adapterFactory: AdapterFactory) {
        this.adapterFactory = adapterFactory
    }

    fun fromCsvImport(trustedRequest: TrustedTrnImportRequest): TrnResponse {
        if (trustedRequest.message != "") {
            log.info(
                "Portfolio {} {}",
                trustedRequest.portfolio.code,
                trustedRequest.message
            )
        }
        if (trustedRequest.row.isNotEmpty()) {
            log.trace("Received Message {}", trustedRequest.toString())
            if (verifyPortfolio(trustedRequest.portfolio.id)) {
                val trnInput = adapterFactory.get(trustedRequest.importFormat)
                    .transform(trustedRequest)
                return writeTrn(trustedRequest.portfolio, trnInput)
            }
        }
        return TrnResponse()
    }

    fun fromTrnRequest(trustedTrnEvent: TrustedTrnEvent): TrnResponse {
        log.trace("Received Message {}", trustedTrnEvent.toString())
        if (verifyPortfolio(trustedTrnEvent.portfolio.id)) {
            val existing = trnService.existing(trustedTrnEvent)
            if (existing.isEmpty()) {
                return writeTrn(trustedTrnEvent.portfolio, trustedTrnEvent.trnInput)
            }
            run {
                log.debug(
                    "Ignoring transaction on {} that already exists",
                    trustedTrnEvent.trnInput.tradeDate
                )
            }
        }
        return TrnResponse()
    }

    private fun writeTrn(portfolio: Portfolio, trnInput: TrnInput): TrnResponse {
        val fxRequest = fxTransactions.buildRequest(portfolio, trnInput)
        val (data) = fxRateService.getRates(fxRequest)
        fxTransactions.setRates(data, fxRequest, trnInput)
        val trnRequest = TrnRequest(portfolio.id, arrayOf(trnInput))
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
