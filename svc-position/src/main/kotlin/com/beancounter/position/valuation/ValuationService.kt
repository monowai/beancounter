package com.beancounter.position.valuation

import com.beancounter.client.services.ClassificationClient
import com.beancounter.client.services.TrnService
import com.beancounter.common.contracts.PositionRequest
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.Currency
import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.PortfolioBreakdown
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.setMarketValue
import com.beancounter.common.telemetry.runBlockingTraced
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.service.MarketValueUpdateProducer
import com.beancounter.position.service.PositionService
import com.beancounter.position.service.PositionValuationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
        private val classificationClient: ClassificationClient,
        private val dateUtils: DateUtils
    ) : Valuation {
        private val log = LoggerFactory.getLogger(ValuationService::class.java)
        private val averageCost = AverageCost()

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
                    trnResponse.data.toTrns()
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
            value: Boolean,
            targetCurrencyCode: String?
        ): PositionResponse {
            if (portfolios.isEmpty()) {
                return PositionResponse()
            }

            // Capture the security context to propagate to coroutines
            val securityContext = SecurityContextHolder.getContext()

            // Concurrently fetch transactions for all portfolios, retaining the
            // portfolio each response belongs to so we can build a per-portfolio
            // breakdown alongside the aggregated view.
            val portfolioTransactions =
                runBlockingTraced(Dispatchers.IO) {
                    portfolios
                        .map { portfolio ->
                            async {
                                SecurityContextHolder.setContext(securityContext)
                                try {
                                    portfolio to trnService.query(portfolio, valuationDate)
                                } finally {
                                    SecurityContextHolder.clearContext()
                                }
                            }
                        }.awaitAll()
                }

            // Accumulate each portfolio's positions INDEPENDENTLY, then sum them.
            //
            // The previous approach merged every portfolio's transactions into a
            // single stream and re-accumulated it. That double-applied any
            // corporate action recorded per-portfolio: a 4:1 split written into
            // each holding portfolio compounded across the combined balance
            // (24 real shares reported as 186), and a SELL in one portfolio drew
            // down a pooled average cost that included another portfolio's lot.
            // Accumulating per portfolio keeps each split and cost basis local;
            // summing the results yields the correct aggregate.
            val perPortfolioPositions =
                portfolioTransactions.mapNotNull { (portfolio, trnResponse) ->
                    val trns = trnResponse.data.toTrns()
                    if (trns.isEmpty()) {
                        null
                    } else {
                        portfolio to positionService.build(portfolio, PositionRequest(portfolio.id, trns)).data
                    }
                }

            if (perPortfolioPositions.isEmpty()) {
                return PositionResponse()
            }

            // Use the first portfolio as context for owner / ids. When the
            // caller passes a [targetCurrencyCode] (the user-visible reporting
            // currency), adopt it on the synthesised context portfolio so
            // MarketValue prices each PORTFOLIO bucket directly against the
            // target rather than via a lossy FX round-trip.
            val baseContext = portfolios.first()
            val contextPortfolio =
                if (!targetCurrencyCode.isNullOrBlank() &&
                    !baseContext.currency.code.equals(targetCurrencyCode, ignoreCase = true)
                ) {
                    baseContext.copy(
                        currency = Currency(targetCurrencyCode.uppercase())
                    )
                } else {
                    baseContext
                }

            val aggregated = mergePositions(contextPortfolio, perPortfolioPositions.map { it.second })

            // Attach per-portfolio breakdown so the UI can list which portfolios hold each asset.
            applyPortfolioBreakdown(aggregated, perPortfolioPositions)

            if (!valuationDate.equals(DateUtils.TODAY, ignoreCase = true)) {
                aggregated.asAt = valuationDate
            }

            // Value the positions if requested.
            // Pass skipMarketValueUpdate=true since aggregated positions don't belong to a single portfolio.
            return if (value) {
                value(
                    positions = aggregated,
                    skipMarketValueUpdate = true
                )
            } else {
                PositionResponse(aggregated)
            }
        }

        /**
         * Sum independently-accumulated per-portfolio positions into a single
         * aggregate keyed by asset. Quantities, cost and cash-flow history add
         * directly; TRADE and BASE buckets share a currency across a user's
         * portfolios so they sum exactly. The PORTFOLIO bucket adopts the
         * context (reporting) currency — exact when the portfolios share it.
         * Market value, gains and IRR are (re)derived later by [value].
         */
        private fun mergePositions(
            contextPortfolio: Portfolio,
            perPortfolio: List<Positions>
        ): Positions {
            val aggregated = Positions(contextPortfolio)
            perPortfolio.forEach { positions ->
                positions.positions.values.forEach { source ->
                    val target = aggregated.getOrCreate(source.asset)
                    mergeQuantities(target, source)
                    source.moneyValues.forEach { (bucket, sourceMv) ->
                        mergeMoneyValues(target.getMoneyValues(bucket, sourceMv.currency), sourceMv)
                    }
                    target.periodicCashFlows.addAll(source.periodicCashFlows.cashFlows)
                    mergeDates(target, source)
                }
            }
            // Restate unit cost from the summed basis so display stays consistent.
            aggregated.positions.values.forEach { position ->
                val total = position.quantityValues.getTotal()
                position.moneyValues.values.forEach { mv ->
                    mv.averageCost = averageCost.value(mv.costBasis, total)
                }
            }
            return aggregated
        }

        private fun mergeQuantities(
            target: Position,
            source: Position
        ) {
            target.quantityValues.purchased = target.quantityValues.purchased.add(source.quantityValues.purchased)
            target.quantityValues.sold = target.quantityValues.sold.add(source.quantityValues.sold)
            target.quantityValues.adjustment = target.quantityValues.adjustment.add(source.quantityValues.adjustment)
        }

        private fun mergeMoneyValues(
            target: MoneyValues,
            source: MoneyValues
        ) {
            target.costValue = target.costValue.add(source.costValue)
            target.costBasis = target.costBasis.add(source.costBasis)
            target.purchases = target.purchases.add(source.purchases)
            target.sales = target.sales.add(source.sales)
            target.dividends = target.dividends.add(source.dividends)
            target.fees = target.fees.add(source.fees)
            target.expenses = target.expenses.add(source.expenses)
            target.realisedGain = target.realisedGain.add(source.realisedGain)
        }

        private fun mergeDates(
            target: Position,
            source: Position
        ) {
            target.dateValues.firstTransaction =
                earliest(target.dateValues.firstTransaction, source.dateValues.firstTransaction)
            target.dateValues.opened = earliest(target.dateValues.opened, source.dateValues.opened)
        }

        private fun earliest(
            a: LocalDate?,
            b: LocalDate?
        ): LocalDate? =
            when {
                a == null -> b
                b == null -> a
                else -> if (a.isBefore(b)) a else b
            }

        /**
         * For each contributing portfolio, record the asset quantity it holds and
         * attach that list to the matching aggregated position. Portfolios with
         * zero net quantity in an asset are skipped.
         */
        private fun applyPortfolioBreakdown(
            aggregated: Positions,
            perPortfolioPositions: List<Pair<Portfolio, Positions>>
        ) {
            if (!aggregated.hasPositions()) return

            val breakdownByAsset = mutableMapOf<String, MutableList<PortfolioBreakdown>>()
            perPortfolioPositions.forEach { (portfolio, positions) ->
                positions.positions.values.forEach { position ->
                    val quantity = position.quantityValues.getTotal()
                    if (quantity.signum() == 0) return@forEach
                    breakdownByAsset
                        .getOrPut(position.asset.id) { mutableListOf() }
                        .add(
                            PortfolioBreakdown(
                                portfolioId = portfolio.id,
                                portfolioCode = portfolio.code,
                                portfolioName = portfolio.name,
                                quantity = quantity
                            )
                        )
                }
            }

            aggregated.positions.values.forEach { position ->
                breakdownByAsset[position.asset.id]?.let { position.portfolioBreakdown = it.toList() }
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
            if (!skipMarketValueUpdate && dateUtils.isToday(positions.asAt)) {
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

            log.trace(
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
        private fun parseValuationDate(asAt: String?): LocalDate = dateUtils.getDate(asAt ?: DateUtils.TODAY)
    }