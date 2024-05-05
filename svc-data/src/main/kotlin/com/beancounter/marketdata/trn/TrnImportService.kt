package com.beancounter.marketdata.trn

import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.marketdata.portfolio.PortfolioService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class TrnImportService(
    private val adapterFactory: AdapterFactory,
    private val portfolioService: PortfolioService,
    private val trnService: TrnService,
) {
    fun fromCsvImport(trustedRequest: TrustedTrnImportRequest): Collection<Trn> {
        if (trustedRequest.message != "") {
            logger.info(
                "Portfolio {} {}",
                trustedRequest.portfolio.code,
                trustedRequest.message,
            )
        }
        if (trustedRequest.row.isEmpty()) {
            return emptySet()
        }
        logger.trace("Received Message {}", trustedRequest.toString())
        if (!verifyPortfolio(trustedRequest.portfolio.id)) {
            return emptySet()
        }
        val trnInput = adapterFactory.get(trustedRequest.importFormat).transform(trustedRequest)
        return writeTrn(trustedRequest.portfolio, trnInput)
    }

    fun fromTrnRequest(trustedTrnEvent: TrustedTrnEvent): Collection<Trn> {
        logger.trace("Message {}", trustedTrnEvent.toString())
        if (!verifyPortfolio(trustedTrnEvent.portfolio.id)) {
            return emptySet()
        }
        val existing = trnService.existing(trustedTrnEvent)
        if (existing.isNotEmpty()) {
            logger.debug(
                "Ignoring " +
                    "tradeDate: ${trustedTrnEvent.trnInput.tradeDate}, " +
                    "assetId: ${trustedTrnEvent.trnInput.assetId}, " +
                    "portfolioId: ${trustedTrnEvent.portfolio.id}",
            )
            return emptySet()
        }
        return writeTrn(trustedTrnEvent.portfolio, trustedTrnEvent.trnInput)
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
            logger.debug("Portfolio {} no longer exists. Ignoring", portfolioId)
            return false
        }
        return true
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TrnImportService::class.java)
    }
}
