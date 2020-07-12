package com.beancounter.position.service

import com.beancounter.client.services.TrnService
import com.beancounter.common.contracts.PositionRequest
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.valuation.Gains
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import java.util.*

/**
 * Values requested positions against market prices.
 *
 * @author mikeh
 * @since 2019-02-24
 */
@Configuration
@Service
class ValuationService @Autowired internal constructor(private val positionValuationService: PositionValuationService,
                                                       private val trnService: TrnService,
                                                       private val positionService: PositionService,
                                                       private val gains: Gains) : Valuation {
    private val dateUtils = DateUtils()
    override fun build(trnQuery: TrustedTrnQuery): PositionResponse {
        val trnResponse = trnService.query(trnQuery)
        return buildPositions(trnQuery.portfolio,
                dateUtils.getDateString(trnQuery.tradeDate), trnResponse)
    }

    override fun build(portfolio: Portfolio, valuationDate: String): PositionResponse {
        val trnResponse = trnService.query(portfolio)
        return buildPositions(portfolio, valuationDate, trnResponse)
    }

    private fun buildPositions(
            portfolio: Portfolio,
            valuationDate: String?,
            trnResponse: TrnResponse): PositionResponse {
        val positionRequest = PositionRequest(portfolio.id, trnResponse.data)
        val positionResponse = positionService.build(portfolio, positionRequest)
        if (valuationDate != null && !valuationDate.equals("today", ignoreCase = true)) {
            positionResponse.data.asAt = valuationDate
        }
        return positionResponse
    }

    override fun value(positions: Positions): PositionResponse {
        if (positions.asAt != null) {
            dateUtils.getOrThrow(positions.asAt)
        }
        val assets: MutableCollection<AssetInput> = ArrayList()
        if (positions.hasPositions()) {
            for (position in positions.positions.values) {
                gains.value(position.quantityValues.getTotal(),
                        position.getMoneyValues(Position.In.PORTFOLIO, positions.portfolio.currency))
                assets.add(AssetInput(position.asset.market.code, position.asset.code))
            }
            val valuedPositions = positionValuationService.value(positions, assets)
            return PositionResponse(valuedPositions)
        }
        return PositionResponse(positions)
    }

}