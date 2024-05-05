package com.beancounter.marketdata.trn

import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.marketdata.fx.FxRateService
import com.beancounter.marketdata.portfolio.PortfolioService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * Controls the ingestion of transaction into the BC format by acting on a TrustedTrnRequest.
 * Verifies the requested portfolio can be accessed and back-fill missing FX Rates.
 *
 * Transformations are delegated to the appropriate TrnAdapter
 */
@Service
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

    fun fromCsvImport(trustedRequest: TrustedTrnImportRequest): Collection<Trn> {
        if (trustedRequest.message != "") {
            log.info(
                "Portfolio {} {}",
                trustedRequest.portfolio.code,
                trustedRequest.message,
            )
        }
        if (trustedRequest.row.isNotEmpty()) {
            log.trace("Received Message {}", trustedRequest.toString())
            if (verifyPortfolio(trustedRequest.portfolio.id)) {
                val trnInput =
                    adapterFactory.get(trustedRequest.importFormat)
                        .transform(trustedRequest)
                return writeTrn(trustedRequest.portfolio, trnInput)
            }
        }
        return emptySet()
    }

    fun fromTrnRequest(trustedTrnEvent: TrustedTrnEvent): Collection<Trn> {
        log.trace("Received Message {}", trustedTrnEvent.toString())
        if (verifyPortfolio(trustedTrnEvent.portfolio.id)) {
            val existing = trnService.existing(trustedTrnEvent)
            if (existing.isEmpty()) {
                return writeTrn(trustedTrnEvent.portfolio, trustedTrnEvent.trnInput)
            }
            run {
                if (!existing.isEmpty()) {
                    log.debug(
                        "Ignoring " +
                            "tradeDate: ${trustedTrnEvent.trnInput.tradeDate}, " +
                            "assetId: ${trustedTrnEvent.trnInput.assetId}, " +
                            "portfolioId: ${trustedTrnEvent.portfolio.id}",
                    )
                }
            }
        }
        return emptySet()
    }

    private fun writeTrn(
        portfolio: Portfolio,
        trnInput: TrnInput,
    ): Collection<Trn> {
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
