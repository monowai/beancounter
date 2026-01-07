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
import java.time.LocalDate
import java.util.Locale

/**
 * Generate a BC corporate event from an AlphaVantage data row.
 */
class AlphaEventAdapter(
    private val taxService: TaxService,
    private val daysToAdd: Long = DEFAULT_DAYS_TO_ADD
) : Event {
    private val dateUtils = DateUtils()

    companion object {
        const val DEFAULT_DAYS_TO_ADD = 10L
    }

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

    /**
     * Calculate the default pay date for a dividend event (recordDate + daysToAdd).
     */
    fun calculatePayDate(corporateEvent: CorporateEvent): LocalDate = corporateEvent.recordDate.plusDays(daysToAdd)

    override fun calculate(
        portfolio: Portfolio,
        currentPosition: Position,
        corporateEvent: CorporateEvent,
        overridePayDate: String?
    ): TrustedTrnEvent {
        if (corporateEvent.trnType == TrnType.DIVI) {
            // Use override if provided, otherwise calculate default (recordDate + configured days)
            val payDate =
                if (overridePayDate != null) {
                    dateUtils.getFormattedDate(overridePayDate)
                } else {
                    calculatePayDate(corporateEvent)
                }
            return TrustedTrnEvent(
                portfolio,
                trnInput =
                    toDividend(
                        currentPosition,
                        corporateEvent,
                        payDate
                    )
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
        corporateEvent: CorporateEvent,
        payDate: LocalDate
    ): TrnInput {
        // All dividends are created as PROPOSED transactions regardless of pay date.
        // A daily scheduler will auto-settle them when the tradeDate arrives.
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