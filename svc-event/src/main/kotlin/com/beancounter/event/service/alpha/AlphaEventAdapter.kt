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
import com.beancounter.common.utils.MathUtils.Companion.multiplyAbs
import com.beancounter.common.utils.MathUtils.Companion.nullSafe
import com.beancounter.event.service.Event
import com.beancounter.event.service.TaxService
import java.math.BigDecimal
import java.util.Locale

/**
 * Generate a BC corporate event from an AlphaVantage data row.
 */
class AlphaEventAdapter(
    private val taxService: TaxService
) : Event {
    private val dateUtils = DateUtils()

    private fun calculateGross(
        currentPosition: Position?,
        rate: BigDecimal?
    ): BigDecimal =
        nullSafe(
            multiplyAbs(
                currentPosition!!.quantityValues.getTotal(),
                rate
            )
        )

    private fun calculateTax(
        currentPosition: Position?,
        gross: BigDecimal?
    ): BigDecimal =
        nullSafe(
            multiplyAbs(
                gross,
                taxService.getRate(
                    currentPosition!!
                        .asset.market.currency.code
                )
            )
        )

    override fun calculate(
        portfolio: Portfolio,
        currentPosition: Position,
        corporateEvent: CorporateEvent
    ): TrustedTrnEvent {
        if (corporateEvent.trnType == TrnType.DIVI) {
            val trnInput =
                toDividend(
                    currentPosition,
                    corporateEvent
                )
                    ?: return TrustedTrnEvent(
                        portfolio,
                        trnInput = TrnInput(trnType = TrnType.IGNORE)
                    ) // We didn't create anything
            return TrustedTrnEvent(
                portfolio,
                trnInput = trnInput
            )
        } else if (corporateEvent.trnType == TrnType.SPLIT) {
            return TrustedTrnEvent(
                portfolio,
                trnInput =
                    toSplit(
                        currentPosition,
                        corporateEvent
                    )
            )
        }
        throw SystemException(
            String.format(
                Locale.US,
                "Unsupported event type %s",
                corporateEvent.trnType
            )
        )
    }

    private fun toSplit(
        currentPosition: Position,
        corporateEvent: CorporateEvent
    ): TrnInput {
        val callerRef =
            CallerRef(
                corporateEvent.source,
                corporateEvent.id!!
            )
        return TrnInput(
            callerRef,
            currentPosition.asset.id,
            trnType = TrnType.SPLIT,
            quantity = corporateEvent.split,
            tradeDate = corporateEvent.recordDate,
            price = corporateEvent.split,
            status = TrnStatus.CONFIRMED,
            cashCurrency = currentPosition.asset.market.currency.code
        )
    }

    private fun toDividend(
        currentPosition: Position,
        corporateEvent: CorporateEvent
    ): TrnInput? {
        val payDate = corporateEvent.recordDate.plusDays(18)
        if (payDate != null) {
            if (payDate > dateUtils.date) {
                return null // Don't create forward dated transactions
            }
        }
        val gross =
            calculateGross(
                currentPosition,
                corporateEvent.rate
            )
        val tax =
            calculateTax(
                currentPosition,
                gross
            )
        val callerRef =
            CallerRef(
                corporateEvent.source,
                corporateEvent.id!!
            )
        return TrnInput(
            callerRef,
            currentPosition.asset.id,
            trnType = TrnType.DIVI,
            quantity = currentPosition.quantityValues.getTotal(),
            tradeDate = payDate,
            tradeAmount = gross.subtract(tax),
            price = corporateEvent.rate,
            status = TrnStatus.PROPOSED,
            cashCurrency = currentPosition.asset.market.currency.code,
            tradeCurrency = currentPosition.asset.market.currency.code,
            tax = tax
        )
    }
}