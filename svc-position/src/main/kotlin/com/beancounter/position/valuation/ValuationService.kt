package com.beancounter.position.valuation

import com.beancounter.client.services.ClassificationClient
import com.beancounter.client.services.TrnService
import com.beancounter.common.contracts.PositionRequest
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.setMarketValue
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.service.MarketValueUpdateProducer
import com.beancounter.position.service.PositionService
import com.beancounter.position.service.PositionValuationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Values requested positions against market prices.
 *
 * @author mikeh
 * @since 2019-02-24
 */
@Configuration
@Service
class ValuationService
    @Autowired
    internal constructor(
        private val positionValuationService: PositionValuationService,
        private val trnService: TrnService,
        private val positionService: PositionService,
        private val marketValueUpdateProducer: MarketValueUpdateProducer,
        private val classificationClient: ClassificationClient
    ) : Valuation {
        private val log = LoggerFactory.getLogger(ValuationService::class.java)

        override fun build(trnQuery: TrustedTrnQuery): PositionResponse {
            val trnResponse = trnService.query(trnQuery) // Adhoc query
            return buildPositions(
                trnQuery.portfolio,
                trnQuery.tradeDate.toString(),
                trnResponse
            )
        }

        override fun build(
            portfolio: Portfolio,
            valuationDate: String
        ): PositionResponse =
            buildInternal(
                portfolio,
                valuationDate
            )

        private fun buildInternal(
            portfolio: Portfolio,
            valuationDate: String
        ): PositionResponse {
            val trnResponse =
                trnService.query(
                    portfolio,
                    valuationDate
                )
            return buildPositions(
                portfolio,
                valuationDate,
                trnResponse
            )
        }

        private fun buildPositions(
            portfolio: Portfolio,
            valuationDate: String,
            trnResponse: TrnResponse
        ): PositionResponse {
            val positionRequest =
                PositionRequest(
                    portfolio.id,
                    trnResponse.data
                )
            val positionResponse =
                positionService.build(
                    portfolio,
                    positionRequest
                )
            if (!valuationDate.equals(
                    DateUtils.TODAY,
                    ignoreCase = true
                )
            ) {
                positionResponse.data.asAt = valuationDate
            }
            return positionResponse
        }

        override fun getPositions(
            portfolio: Portfolio,
            valuationDate: String,
            value: Boolean
        ): PositionResponse {
            val positions =
                build(
                    portfolio,
                    valuationDate
                ).data
            return if (value) value(positions) else PositionResponse(positions)
        }

        override fun getAggregatedPositions(
            portfolios: Collection<Portfolio>,
            valuationDate: String,
            value: Boolean
        ): PositionResponse {
            if (portfolios.isEmpty()) {
                return PositionResponse()
            }

            // Capture the security context to propagate to coroutines
            val securityContext = SecurityContextHolder.getContext()

            // Concurrently fetch transactions for all portfolios
            val allTransactions =
                runBlocking(Dispatchers.IO) {
                    portfolios
                        .map { portfolio ->
                            async {
                                SecurityContextHolder.setContext(securityContext)
                                try {
                                    trnService.query(portfolio, valuationDate)
                                } finally {
                                    SecurityContextHolder.clearContext()
                                }
                            }
                        }.awaitAll()
                }

            // Combine all transactions and sort by trade date
            // Sorting is required because the Accumulator validates date sequence
            val combinedTransactions =
                allTransactions
                    .flatMap { it.data }
                    .sortedBy { it.tradeDate }

            if (combinedTransactions.isEmpty()) {
                return PositionResponse()
            }

            // Use the first portfolio as context for currency settings and owner
            // No fake "AGGREGATED" portfolio is created - positions are built using
            // the first portfolio's context but contain aggregated transaction data
            val contextPortfolio = portfolios.first()

            // Build positions from combined transactions using the normal accumulation flow
            val positionRequest =
                PositionRequest(
                    contextPortfolio.id,
                    combinedTransactions
                )
            val positionResponse = positionService.build(contextPortfolio, positionRequest)

            if (!valuationDate.equals(DateUtils.TODAY, ignoreCase = true)) {
                positionResponse.data.asAt = valuationDate
            }

            // Value the positions if requested
            // Pass skipMarketValueUpdate=true since aggregated positions don't belong to a single portfolio
            return if (value) {
                value(
                    positions = positionResponse.data,
                    skipMarketValueUpdate = true
                )
            } else {
                positionResponse
            }
        }

        override fun value(positions: Positions): PositionResponse = value(positions, skipMarketValueUpdate = false)

        /**
         * Values positions with optional control over market value updates.
         *
         * @param positions positions to value
         * @param skipMarketValueUpdate if true, don't send market value updates (used for aggregated positions)
         * @return valued positions
         */
        private fun value(
            positions: Positions,
            skipMarketValueUpdate: Boolean
        ): PositionResponse {
            if (!positions.hasPositions()) {
                return PositionResponse(positions)
            }

            val assets =
                positions.positions.values.map {
                    AssetInput(
                        it.asset.market.code,
                        it.asset.code
                    )
                }
            val valuedPositions =
                positionValuationService.value(
                    positions,
                    assets.toList()
                )

            // Enrich positions with classification data (sector/industry)
            enrichWithClassifications(valuedPositions)

            // Send market value updates for individual portfolio valuations (not aggregated)
            // Only update when viewing current positions ("today"), not historical
            if (!skipMarketValueUpdate && DateUtils().isToday(positions.asAt)) {
                sendMarketValueUpdate(valuedPositions)
            }
            return PositionResponse(valuedPositions)
        }

        /**
         * Enriches positions with sector/industry classification data from svc-data.
         * Cash assets are always classified as "Cash" sector.
         * Failures are logged but don't block the response - positions just lack classification data.
         */
        private fun enrichWithClassifications(positions: Positions) {
            if (!positions.hasPositions()) return

            val assetIds = positions.positions.values.map { it.asset.id }
            val classifications = classificationClient.getClassifications(assetIds)

            positions.positions.values.forEach { position ->
                // Cash assets always have sector "Cash"
                if (position.asset.effectiveReportCategory == AssetCategory.REPORT_CASH) {
                    position.asset.sector = AssetCategory.REPORT_CASH
                } else {
                    classifications.data[position.asset.id]?.let { classification ->
                        position.asset.sector = classification.sector
                        position.asset.industry = classification.industry
                    }
                }
            }
        }

        /**
         * Sends market value update to the message broker.
         * Market value is sent in portfolio.base currency - frontend handles display currency conversion.
         */
        private fun sendMarketValueUpdate(valuedPositions: Positions) {
            val portfolio = valuedPositions.portfolio
            val baseTotals = valuedPositions.totals[Position.In.BASE]

            // Store market value in portfolio.base currency
            // Frontend handles conversion to display currency
            val marketValue = baseTotals?.marketValue ?: BigDecimal.ZERO
            val irr = baseTotals?.irr ?: BigDecimal.ZERO

            // Calculate gain on day from all positions
            val gainOnDay = calculateGainOnDay(valuedPositions)

            // Calculate asset classification breakdown
            val assetClassification = calculateAssetClassification(valuedPositions)

            // Get the valuation date
            val valuedAt = parseValuationDate(valuedPositions.asAt)

            log.info(
                "Sending MV update: portfolio={}, base={}, marketValue={}, gainOnDay={}, valuedAt={}",
                portfolio.code,
                portfolio.base.code,
                marketValue,
                gainOnDay,
                valuedAt
            )

            val updateTo = portfolio.setMarketValue(marketValue, irr, gainOnDay, assetClassification, valuedAt)
            marketValueUpdateProducer.sendMessage(updateTo)
        }

        /**
         * Calculate total gain on day by summing gainOnDay from all positions in BASE currency.
         */
        private fun calculateGainOnDay(positions: Positions): BigDecimal =
            positions.positions.values
                .mapNotNull { it.moneyValues[Position.In.BASE]?.gainOnDay }
                .fold(BigDecimal.ZERO) { acc, value -> acc.add(value) }

        /**
         * Calculate asset classification breakdown by grouping positions by effectiveReportCategory
         * and summing their market values in BASE currency.
         */
        private fun calculateAssetClassification(positions: Positions): Map<String, BigDecimal> =
            positions.positions.values
                .groupBy { it.asset.effectiveReportCategory }
                .mapValues { (_, positionsInCategory) ->
                    positionsInCategory
                        .mapNotNull { it.moneyValues[Position.In.BASE]?.marketValue }
                        .fold(BigDecimal.ZERO) { acc, value -> acc.add(value) }
                }

        /**
         * Parse the valuation date from asAt string.
         * Returns today's date if asAt is "today" or null, otherwise parses the date string.
         */
        private fun parseValuationDate(asAt: String?): LocalDate =
            when {
                asAt.isNullOrBlank() || asAt.equals(DateUtils.TODAY, ignoreCase = true) -> LocalDate.now()
                else -> LocalDate.parse(asAt)
            }
    }