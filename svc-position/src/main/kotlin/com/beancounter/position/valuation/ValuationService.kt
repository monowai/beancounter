package com.beancounter.position.valuation

import com.beancounter.auth.TokenService
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.exception.UnauthorizedException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Positions
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.marketvalue.PositionValuationService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service

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
        private val portfolioPositions: PortfolioPositions,
        private val portfolioConsolidation: PortfolioConsolidation,
        private val marketValueUpdateProducer: MarketValueUpdateProducer,
        private val tokenService: TokenService,
        @Value("\${beancounter.valuation.cache-ttl-seconds:5}")
        private val cacheTtlSeconds: Long = 5
    ) : Valuation {
        private val log = LoggerFactory.getLogger(ValuationService::class.java)

        // Short-TTL, single-flight cache in front of getAggregatedPositions - see
        // AggregatedValuationCache for the evidence and rationale. cache-ttl-seconds=0
        // disables it entirely (every call loads fresh).
        private val aggregatedValuationCache = AggregatedValuationCache(cacheTtlSeconds)

        override fun build(trnQuery: TrustedTrnQuery): PositionResponse =
            PositionResponse(portfolioPositions.of(trnQuery))

        override fun build(
            portfolio: Portfolio,
            valuationDate: String
        ): PositionResponse = PositionResponse(portfolioPositions.of(portfolio, valuationDate))

        override fun getPositions(
            portfolio: Portfolio,
            valuationDate: String,
            value: Boolean
        ): PositionResponse {
            val positions = portfolioPositions.of(portfolio, valuationDate)
            return if (value) value(positions) else PositionResponse(positions)
        }

        /**
         * On a cache hit this returns a [PositionResponse] instance shared with
         * every other caller of that key - see [AggregatedValuationCache]'s
         * read-only contract; callers must not mutate it.
         */
        override fun getAggregatedPositions(request: AggregateValuationRequest): PositionResponse {
            if (request.portfolios.isEmpty()) {
                return PositionResponse()
            }
            return aggregatedValuationCache.get(cacheKey(request)) {
                computeAggregatedPositions(request)
            }
        }

        /**
         * Resolves the caller's JWT subject and builds the cache key for this
         * call. Returns null (cache bypass, never a cross-user risk) when the
         * subject can't be resolved - e.g. no authenticated SecurityContext -
         * rather than caching under a placeholder identity.
         */
        private fun cacheKey(request: AggregateValuationRequest): ValuationCacheKey? =
            try {
                ValuationCacheKey.of(tokenService.subject, request)
            } catch (e: UnauthorizedException) {
                log.debug("Valuation cache bypassed: unable to resolve caller subject ({})", e.javaClass.simpleName)
                null
            }

        private fun computeAggregatedPositions(request: AggregateValuationRequest): PositionResponse {
            val perPortfolio = portfolioPositions.each(request.portfolios, request.valuationDate)
            if (perPortfolio.isEmpty()) {
                return PositionResponse()
            }
            val aggregated = portfolioConsolidation.consolidate(request, perPortfolio)
            if (!request.valuationDate.equals(DateUtils.TODAY, ignoreCase = true)) {
                aggregated.asAt = request.valuationDate
            }
            // An aggregate belongs to no single portfolio, so its value is never published.
            return if (request.value) {
                PositionResponse(valued(aggregated, request.live))
            } else {
                PositionResponse(aggregated)
            }
        }

        override fun value(positions: Positions): PositionResponse {
            if (!positions.hasPositions()) {
                return PositionResponse(positions)
            }
            val valued = valued(positions, live = false)
            marketValueUpdateProducer.publish(valued)
            return PositionResponse(valued)
        }

        /** [positions] at market prices, or at live (delayed) quotes when [live]. */
        private fun valued(
            positions: Positions,
            live: Boolean
        ): Positions {
            if (!positions.hasPositions()) {
                return positions
            }
            val assets = positions.positions.values.map { AssetInput(it.asset.market.code, it.asset.code) }
            return positionValuationService.value(positions, assets, live)
        }
    }