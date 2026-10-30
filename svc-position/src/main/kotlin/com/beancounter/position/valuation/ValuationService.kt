package com.beancounter.position.valuation

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.client.services.TrnService
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.PositionRequest
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.exception.UnauthorizedException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Currency
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.PortfolioBreakdown
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.setMarketValue
import com.beancounter.common.telemetry.runBlockingTraced
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.MathUtils
import com.beancounter.position.service.MarketValueUpdateProducer
import com.beancounter.position.service.PositionService
import com.beancounter.position.service.PositionValuationService
import com.beancounter.position.utils.runAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
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
        private val fxRateService: FxService,
        private val tokenService: TokenService,
        private val dateUtils: DateUtils,
        private val earmarkService: EarmarkService,
        @Value("\${beancounter.valuation.cache-ttl-seconds:5}")
        private val cacheTtlSeconds: Long = 5
    ) : Valuation {
        private val log = LoggerFactory.getLogger(ValuationService::class.java)
        private val averageCost = AverageCost()

        // Short-TTL, single-flight cache in front of getAggregatedPositions - see
        // AggregatedValuationCache for the evidence and rationale. cache-ttl-seconds=0
        // disables it entirely (every call loads fresh).
        private val aggregatedValuationCache = AggregatedValuationCache(cacheTtlSeconds)

        override fun build(trnQuery: TrustedTrnQuery): PositionResponse {
            val trnResponse = trnService.query(trnQuery) // Adhoc query
            val valuationDate = trnQuery.tradeDate.toString()
            return buildPositions(
                trnQuery.portfolio,
                valuationDate,
                trnResponse,
                proposedLegs(trnQuery.portfolio, valuationDate)
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
            val reads = readPortfolios(listOf(portfolio), valuationDate).single()
            return buildPositions(
                portfolio,
                valuationDate,
                reads.trnResponse,
                reads.proposedLegs
            )
        }

        /**
         * Reads each portfolio's trns and proposed cash legs from bc-data, every
         * call at once. Neither read depends on the other, and the clients block,
         * so they only overlap on the IO pool, each bound to the caller.
         */
        private fun readPortfolios(
            portfolios: Collection<Portfolio>,
            valuationDate: String
        ): List<PortfolioReads> {
            val caller = SecurityContextHolder.getContext()
            return runBlockingTraced(Dispatchers.IO) {
                portfolios
                    .map { portfolio ->
                        Triple(
                            portfolio,
                            async { caller.runAs { trnService.query(portfolio, valuationDate) } },
                            async { caller.runAs { proposedLegs(portfolio, valuationDate) } }
                        )
                    }.map { (portfolio, trns, legs) -> PortfolioReads(portfolio, trns.await(), legs.await()) }
            }
        }

        private fun buildPositions(
            portfolio: Portfolio,
            valuationDate: String,
            trnResponse: TrnResponse,
            proposedLegs: List<Trn>
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
            // Stamp signed earmarked cash-ccy quantity BEFORE market-value runs so
            // MarketValue can mint MoneyValues.earmarked from it per bucket.
            earmarkService.stamp(positionResponse.data, proposedLegs)
            return positionResponse
        }

        /**
         * Earmarked cash is an overlay on the cash position, read from bc-data's
         * proposed-cash route with the caller's own token. A home-screen widget's
         * token carries only `beancounter:widget`, which has no right to that route
         * (it is not in bc-data's `auth.widget-routes`), and the widget shows
         * percentages only and never displays the overlay. So a widget-scoped
         * valuation skips the read outright rather than taking a 403 per portfolio;
         * enough of those once opened the shared bc-data circuit breaker.
         */
        private fun proposedLegs(
            portfolio: Portfolio,
            valuationDate: String
        ): List<Trn> =
            if (tokenService.isWidgetToken) {
                emptyList()
            } else {
                earmarkService.proposedLegs(portfolio, valuationDate)
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

        /**
         * On a cache hit this returns a [PositionResponse] instance shared with
         * every other caller of that key - see [AggregatedValuationCache]'s
         * read-only contract; callers must not mutate it.
         */
        override fun getAggregatedPositions(
            portfolios: Collection<Portfolio>,
            valuationDate: String,
            value: Boolean,
            targetCurrencyCode: String?,
            live: Boolean
        ): PositionResponse {
            if (portfolios.isEmpty()) {
                return PositionResponse()
            }
            val cacheKey = buildCacheKey(portfolios, valuationDate, value, targetCurrencyCode, live)
            return aggregatedValuationCache.get(cacheKey) {
                computeAggregatedPositions(portfolios, valuationDate, value, targetCurrencyCode, live)
            }
        }

        /**
         * Resolves the caller's JWT subject and builds the cache key for this
         * call. Returns null (cache bypass, never a cross-user risk) when the
         * subject can't be resolved - e.g. no authenticated SecurityContext -
         * rather than caching under a placeholder identity.
         */
        private fun buildCacheKey(
            portfolios: Collection<Portfolio>,
            valuationDate: String,
            value: Boolean,
            targetCurrencyCode: String?,
            live: Boolean
        ): ValuationCacheKey? =
            try {
                ValuationCacheKey.of(
                    subject = tokenService.subject,
                    portfolios = portfolios,
                    valuationDate = valuationDate,
                    value = value,
                    targetCurrencyCode = targetCurrencyCode,
                    live = live
                )
            } catch (e: UnauthorizedException) {
                log.debug("Valuation cache bypassed: unable to resolve caller subject ({})", e.javaClass.simpleName)
                null
            }

        private fun computeAggregatedPositions(
            portfolios: Collection<Portfolio>,
            valuationDate: String,
            value: Boolean,
            targetCurrencyCode: String?,
            live: Boolean
        ): PositionResponse {
            // Read every portfolio at once, retaining the portfolio each response
            // belongs to so we can build a per-portfolio breakdown alongside the
            // aggregated view.
            val portfolioReads = readPortfolios(portfolios, valuationDate)

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
                portfolioReads.mapNotNull { (portfolio, trnResponse, proposedLegs) ->
                    val trns = trnResponse.data.toTrns()
                    if (trns.isEmpty()) {
                        null
                    } else {
                        val built = positionService.build(portfolio, PositionRequest(portfolio.id, trns)).data
                        // Stamp each per-portfolio Positions with ITS own portfolio's
                        // proposed cash legs before the merge sums earmarkedQuantity.
                        earmarkService.stamp(built, proposedLegs)
                        portfolio to built
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

            // The cost buckets are accumulated in each source portfolio's own
            // currency. When the aggregate reports in a different currency (or
            // spans portfolios with differing currencies) those costs must be
            // FX-converted before summing, otherwise a raw foreign figure sits
            // under the reporting-currency label. Market value is (re)priced
            // later against the target rate; this keeps cost consistent with it.
            val sourcePositions = perPortfolioPositions.map { it.second }
            val rates = aggregationRates(contextPortfolio, sourcePositions, valuationDate)
            val aggregated = mergePositions(contextPortfolio, sourcePositions, rates)

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
                    skipMarketValueUpdate = true,
                    live = live
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
            perPortfolio: List<Positions>,
            rates: Map<IsoCurrencyPair, FxRate>
        ): Positions {
            val aggregated = Positions(contextPortfolio)
            perPortfolio.forEach { positions ->
                positions.positions.values.forEach { source ->
                    val target = aggregated.getOrCreate(source.asset)
                    mergeQuantities(target, source)
                    source.moneyValues.forEach { (bucket, sourceMv) ->
                        val targetCurrency = targetBucketCurrency(bucket, sourceMv.currency, contextPortfolio)
                        mergeMoneyValues(
                            target.getMoneyValues(bucket, targetCurrency),
                            sourceMv,
                            conversionRate(sourceMv.currency, targetCurrency, rates)
                        )
                    }
                    target.periodicCashFlows.addAll(source.periodicCashFlows.cashFlows)
                    // Per-broker holdings survive aggregation so the UI can cap
                    // a sell at what the chosen broker actually holds.
                    source.held.forEach { (broker, qty) ->
                        target.held.merge(broker, qty, BigDecimal::add)
                    }
                    source.subAccounts.forEach { (code, balance) ->
                        target.subAccounts.merge(code, balance, BigDecimal::add)
                    }
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

        /**
         * The currency each aggregated bucket is denominated in. TRADE stays in
         * the asset's own trade currency (shared across a user's portfolios);
         * PORTFOLIO adopts the context (reporting) currency and BASE the context
         * base, so summed cost lands in the same currency as the (re)priced
         * market value.
         */
        private fun targetBucketCurrency(
            bucket: Position.In,
            sourceCurrency: Currency,
            context: Portfolio
        ): Currency =
            when (bucket) {
                Position.In.TRADE -> sourceCurrency
                Position.In.PORTFOLIO -> context.currency
                Position.In.BASE -> context.base
            }

        /**
         * Fetch the spot FX rates needed to convert each source bucket's cost
         * into its aggregated (reporting) currency. Same-currency buckets need
         * no rate; when every bucket already matches the context currency the
         * request is skipped entirely (the common single-currency case).
         *
         * Cost basis is accumulated at many historical rates, so a single spot
         * rate is an approximation — mirroring how market value is priced and
         * how the frontend flags CUSTOM-currency cost as approximate.
         */
        private fun aggregationRates(
            contextPortfolio: Portfolio,
            perPortfolio: List<Positions>,
            valuationDate: String
        ): Map<IsoCurrencyPair, FxRate> {
            val pairs = mutableSetOf<IsoCurrencyPair>()
            perPortfolio.forEach { positions ->
                positions.positions.values.forEach { position ->
                    position.moneyValues.forEach { (bucket, mv) ->
                        IsoCurrencyPair
                            .toPair(
                                mv.currency,
                                targetBucketCurrency(bucket, mv.currency, contextPortfolio)
                            )?.let { pairs.add(it) }
                    }
                }
            }
            if (pairs.isEmpty()) return emptyMap()

            val fxRequest = FxRequest(rateDate = valuationDate)
            pairs.forEach { fxRequest.add(it) }
            return fxRateService.getRates(fxRequest, tokenService.bearerToken).data.rates
        }

        private fun conversionRate(
            from: Currency,
            to: Currency,
            rates: Map<IsoCurrencyPair, FxRate>
        ): BigDecimal {
            if (from.code == to.code) return BigDecimal.ONE
            val rate = rates[IsoCurrencyPair(from.code, to.code)]?.rate
            if (rate == null) {
                log.warn(
                    "No FX rate for {}:{} while aggregating cost; leaving unconverted",
                    from.code,
                    to.code
                )
                return BigDecimal.ONE
            }
            return rate
        }

        private fun mergeQuantities(
            target: Position,
            source: Position
        ) {
            target.quantityValues.purchased = target.quantityValues.purchased.add(source.quantityValues.purchased)
            target.quantityValues.sold = target.quantityValues.sold.add(source.quantityValues.sold)
            target.quantityValues.adjustment = target.quantityValues.adjustment.add(source.quantityValues.adjustment)
            target.earmarkedQuantity = target.earmarkedQuantity.add(source.earmarkedQuantity)
        }

        private fun mergeMoneyValues(
            target: MoneyValues,
            source: MoneyValues,
            rate: BigDecimal
        ) {
            target.costValue = target.costValue.add(convert(source.costValue, rate))
            target.costBasis = target.costBasis.add(convert(source.costBasis, rate))
            target.purchases = target.purchases.add(convert(source.purchases, rate))
            target.sales = target.sales.add(convert(source.sales, rate))
            target.dividends = target.dividends.add(convert(source.dividends, rate))
            target.fees = target.fees.add(convert(source.fees, rate))
            target.expenses = target.expenses.add(convert(source.expenses, rate))
            target.realisedGain = target.realisedGain.add(convert(source.realisedGain, rate))
        }

        private fun convert(
            value: BigDecimal,
            rate: BigDecimal
        ): BigDecimal = MathUtils.multiply(value, rate) ?: value

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
                                quantity = quantity,
                                held = position.held.toMap()
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
         * @param live value at live (delayed) quotes; never with a market value update
         * @return valued positions
         */
        private fun value(
            positions: Positions,
            skipMarketValueUpdate: Boolean,
            live: Boolean = false
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
                    assets.toList(),
                    live
                )

            // Send market value updates for individual portfolio valuations (not aggregated)
            // Only update when viewing current positions ("today"), not historical
            if (!skipMarketValueUpdate && !live && dateUtils.isToday(positions.asAt)) {
                sendMarketValueUpdate(valuedPositions)
            }
            return PositionResponse(valuedPositions)
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

/** One portfolio's bc-data reads for a valuation. */
private data class PortfolioReads(
    val portfolio: Portfolio,
    val trnResponse: TrnResponse,
    val proposedLegs: List<Trn>
)