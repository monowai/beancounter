package com.beancounter.event.service.alpha

import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.exception.SystemException
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.model.*
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.MathUtils.Companion.multiply
import com.beancounter.event.service.Event
import com.beancounter.event.service.TaxService
import java.math.BigDecimal

class AlphaEventAdapter(private val taxService: TaxService) : Event {
    private val dateUtils = DateUtils()
    override fun calculate(portfolio: Portfolio,
                           currentPosition: Position, corporateEvent: CorporateEvent): TrustedTrnEvent? {
        if (corporateEvent.trnType == TrnType.DIVI) {
            val trnInput = toDividend(currentPosition, corporateEvent)
                    ?: return null // We didn't create anything
            return TrustedTrnEvent(portfolio, trnInput)
        }
        throw SystemException(String.format("Unsupported event type %s", corporateEvent.trnType))
    }

    private fun toDividend(currentPosition: Position,
                           corporateEvent: CorporateEvent?): TrnInput? {
        if ( corporateEvent == null ) {
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
        val callerRef = CallerRef(corporateEvent.source, corporateEvent.id, null)
        val result = TrnInput(
                callerRef,
                corporateEvent.assetId,
                TrnType.DIVI
        )
        result.status = TrnStatus.PROPOSED
        result.quantity = currentPosition.quantityValues.getTotal()
        result.tradeDate = payDate // Should be PayDate +1
        result.price = corporateEvent.rate
        result.tax = tax
        result.tradeCurrency = currentPosition.asset.market.currency.code
        result.cashCurrency = currentPosition.asset.market.currency.code
        result.tradeAmount = gross!!.subtract(tax)
        return result
    }

    private fun calculateGross(currentPosition: Position?, rate: BigDecimal?): BigDecimal? {
        return multiply(currentPosition!!.quantityValues.getTotal(), rate)
    }

    private fun calculateTax(currentPosition: Position?, gross: BigDecimal?): BigDecimal? {
        return multiply(gross,
                taxService.getRate(currentPosition!!.asset.market.currency.code))
    }

}