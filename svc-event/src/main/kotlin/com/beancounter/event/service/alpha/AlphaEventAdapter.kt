package com.beancounter.event.service.alpha

import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.exception.SystemException
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.MathUtils.Companion.multiply
import com.beancounter.event.service.Event
import com.beancounter.event.service.TaxService
import java.math.BigDecimal

/**
 * Generate a BC corporate event from an AlphaVantage data row.
 */
class AlphaEventAdapter(private val taxService: TaxService) : Event {
    private val dateUtils = DateUtils()

    private fun calculateGross(currentPosition: Position?, rate: BigDecimal?): BigDecimal? {
        return multiply(currentPosition!!.quantityValues.getTotal(), rate)
    }

    private fun calculateTax(currentPosition: Position?, gross: BigDecimal?): BigDecimal? {
        return multiply(
            gross,
            taxService.getRate(currentPosition!!.asset.market.currency.code)
        )
    }

    override fun calculate(
        portfolio: Portfolio,
        currentPosition: Position,
        corporateEvent: CorporateEvent
    ): TrustedTrnEvent? {
        if (corporateEvent.trnType == TrnType.DIVI) {
            val trnInput = toDividend(currentPosition, corporateEvent)
                ?: return null // We didn't create anything
            return TrustedTrnEvent(portfolio, trnInput)
        }
        throw SystemException(String.format("Unsupported event type %s", corporateEvent.trnType))
    }

    private fun toDividend(
        currentPosition: Position,
        corporateEvent: CorporateEvent?
    ): TrnInput? {
        if (corporateEvent == null) {
            return null
        }
        val payDate = corporateEvent.recordDate.plusDays(18)
        if (payDate != null) {
            if (payDate > dateUtils.date) {
                return null // Don't create forward dated transactions
            }
        }
        val gross = calculateGross(currentPosition, corporateEvent.rate)
        val tax = calculateTax(currentPosition, gross)
        val callerRef = CallerRef(corporateEvent.source, corporateEvent.id)
        val result = TrnInput(
            callerRef,
            currentPosition.asset.id,
            TrnType.DIVI,
            currentPosition.quantityValues.getTotal(),
            tradeDate = payDate,
            tradeAmount = gross!!.subtract(tax),
            price = corporateEvent.rate,
            tax = tax!!,

        )
        result.status = TrnStatus.PROPOSED
        result.cashCurrency = currentPosition.asset.market.currency.code
        return result
    }
}
