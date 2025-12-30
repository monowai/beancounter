package com.beancounter.marketdata.trn

import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.marketdata.metrics.TrnMetrics
import com.beancounter.marketdata.portfolio.PortfolioService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service class for importing transaction data.
 */
@Service
class TrnImportService(
    private val adapterFactory: AdapterFactory,
    private val portfolioService: PortfolioService,
    private val trnService: TrnService,
    private val trnMetrics: TrnMetrics
) {
    /**
     * Imports transaction data from a CSV file from a trusted source.
     *
     * @param trustedRequest The request containing the CSV data.
     * @return A collection of transactions.
     */
    fun fromCsvImport(trustedRequest: TrustedTrnImportRequest): Collection<Trn> {
        if (trustedRequest.message != "") {
            logger.info(
                "Portfolio {} {}",
                trustedRequest.portfolio.code,
                trustedRequest.message
            )
        }
        if (trustedRequest.row.isEmpty()) {
            return emptySet()
        }
        logger.trace(
            "Received Message {}",
            trustedRequest.toString()
        )
        if (!verifyPortfolio(trustedRequest.portfolio.id)) {
            return emptySet()
        }
        val trnInput = adapterFactory.get(trustedRequest.importFormat).transform(trustedRequest)
        return writeTrn(
            trustedRequest.portfolio,
            trnInput
        )
    }

    /**
     * Imports corporate actions from a transaction request.
     *
     * @param trustedTrnEvent The event containing the transaction data.
     * @return A collection of transactions.
     */
    fun fromTrnRequest(trustedTrnEvent: TrustedTrnEvent): Collection<Trn> {
        // Record transaction event received
        trnMetrics.recordTrnEventReceived(trustedTrnEvent.trnInput.trnType.name)

        return trnMetrics.timeTransactionImport {
            logger.trace(
                "Message {}",
                trustedTrnEvent.toString()
            )
            if (!verifyPortfolio(trustedTrnEvent.portfolio.id)) {
                trnMetrics.recordTrnIgnored("portfolio_verification_failed")
                return@timeTransactionImport emptySet()
            }
            val existing = trnService.existing(trustedTrnEvent)
            if (existing.isNotEmpty()) {
                // Calculate days difference for metrics
                val daysDiff =
                    existing.firstOrNull()?.let { existingTrn ->
                        java.time.temporal.ChronoUnit.DAYS
                            .between(
                                existingTrn.tradeDate,
                                trustedTrnEvent.trnInput.tradeDate
                            ).let { kotlin.math.abs(it) }
                    }
                trnMetrics.recordDuplicateDetected(
                    trustedTrnEvent.trnInput.trnType.name,
                    daysDiff
                )
                logger.debug(
                    "Ignoring duplicate: " +
                        "tradeDate: ${trustedTrnEvent.trnInput.tradeDate}, " +
                        "assetId: ${trustedTrnEvent.trnInput.assetId}, " +
                        "portfolioId: ${trustedTrnEvent.portfolio.id}, " +
                        "daysDiff: $daysDiff"
                )
                return@timeTransactionImport emptySet()
            }
            val written =
                writeTrn(
                    trustedTrnEvent.portfolio,
                    trustedTrnEvent.trnInput
                )
            trnMetrics.recordTrnWritten(trustedTrnEvent.trnInput.trnType.name, written.size)
            written
        }
    }

    private fun writeTrn(
        portfolio: Portfolio,
        trnInput: TrnInput
    ): Collection<Trn> {
        val trnRequest =
            TrnRequest(
                portfolio.id,
                listOf(trnInput)
            )
        return trnService.save(
            portfolio,
            trnRequest
        )
    }

    private fun verifyPortfolio(portfolioId: String): Boolean {
        if (!portfolioService.verify(portfolioId)) {
            logger.debug(
                "Portfolio {} no longer exists. Ignoring",
                portfolioId
            )
            return false
        }
        return true
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TrnImportService::class.java)
    }
}