package com.beancounter.position.valuation

import com.beancounter.client.services.TrnService
import com.beancounter.common.contracts.PositionRequest
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.TrustedTrnQuery
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.context.SecurityContextHolder
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
        private val trnService: TrnService,
        private val positionService: PositionService,
        private val marketValueUpdateProducer: MarketValueUpdateProducer
    ) : Valuation {
        @Value("kafka.enabled")
        private lateinit var kafkaEnabled: String

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

            // Only send market value updates for individual portfolio valuations (not aggregated)
            if (!skipMarketValueUpdate && DateUtils().isToday(positions.asAt) && !kafkaEnabled.toBoolean()) {
                val portfolio = valuedPositions.portfolio
                val updateTo =
                    portfolio.setMarketValue(
                        valuedPositions.totals[Position.In.PORTFOLIO]!!.marketValue,
                        valuedPositions.totals[Position.In.PORTFOLIO]!!.irr
                    )
                marketValueUpdateProducer.sendMessage(updateTo)
            }
            return PositionResponse(valuedPositions)
        }
    }