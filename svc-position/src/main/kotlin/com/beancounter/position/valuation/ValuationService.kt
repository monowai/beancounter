package com.beancounter.position.valuation

import com.beancounter.client.services.TrnService
import com.beancounter.common.contracts.PositionRequest
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Positions
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.service.PositionService
import com.beancounter.position.service.PositionValuationService
import org.springframework.beans.factory.annotation.Autowired
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
        private val trnService: TrnService,
        private val positionService: PositionService,
        private val dateUtils: DateUtils,
    ) : Valuation {
        override fun build(trnQuery: TrustedTrnQuery): PositionResponse {
            val trnResponse = trnService.query(trnQuery)
            return buildPositions(
                trnQuery.portfolio,
                trnQuery.tradeDate.toString(),
                trnResponse,
            )
        }

        override fun build(
            portfolio: Portfolio,
            valuationDate: String,
        ): PositionResponse {
            val trnResponse = trnService.query(portfolio, valuationDate)
            return buildPositions(portfolio, valuationDate, trnResponse)
        }

        private fun buildPositions(
            portfolio: Portfolio,
            valuationDate: String,
            trnResponse: TrnResponse,
        ): PositionResponse {
            val positionRequest = PositionRequest(portfolio.id, trnResponse.data)
            val positionResponse = positionService.build(portfolio, positionRequest)
            if (!valuationDate.equals(DateUtils.TODAY, ignoreCase = true)) {
                positionResponse.data.asAt = valuationDate
            }
            return positionResponse
        }

        override fun getPositions(
            portfolio: Portfolio,
            valuationDate: String,
            value: Boolean,
        ): PositionResponse {
            val positions = build(portfolio, valuationDate).data
            return if (value) {
                value(positions)
            } else {
                PositionResponse(positions)
            }
        }

        override fun value(positions: Positions): PositionResponse {
            dateUtils.getDate(positions.asAt)
            val assets: MutableCollection<AssetInput> = ArrayList()
            if (positions.hasPositions()) {
                for (position in positions.positions.values) {
                    assets.add(AssetInput(position.asset.market.code, position.asset.code))
                }
                val valuedPositions = positionValuationService.value(positions, assets)
                return PositionResponse(valuedPositions)
            }
            return PositionResponse(positions)
        }
    }
