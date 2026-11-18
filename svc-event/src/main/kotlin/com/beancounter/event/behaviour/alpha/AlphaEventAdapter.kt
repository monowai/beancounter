package com.beancounter.event.behaviour.alpha

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
import com.beancounter.event.behaviour.Event
import com.beancounter.event.behaviour.TaxService
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
        currentPosition: Position,
        rate: BigDecimal?
    ): BigDecimal =
        nullSafe(
            multiplyAbs(
                currentPosition.quantityValues.getTotal(),
                rate
            )
        )

    private fun calculateTax(
        currentPosition: Position,
        gross: BigDecimal?
    ): BigDecimal =
        nullSafe(
            multiplyAbs(
                gross,
                taxService.getRate(
                    currentPosition
                        .asset.market.currency.code
                )
            )
        )

    /**
     * Get the pay date for a dividend event.
     * Uses the actual payDate from the corporate event if available,
     * otherwise falls back to recordDate + daysToAdd.
     */
    fun calculatePayDate(corporateEvent: CorporateEvent): LocalDate =
        if (corporateEvent.payDate != corporateEvent.recordDate) {
            // Use actual payDate from the corporate event when available
            corporateEvent.payDate
        } else {
            // Fall back to default calculation when payDate is not set
            corporateEvent.recordDate.plusDays(daysToAdd)
        }

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

    /**
     * A split is not elective, so one dated today or earlier is SETTLED: it has happened, and a
     * backfill records it as fact. A future one is PROPOSED, so the user may still cancel it.
     */
    private fun statusOn(tradeDate: LocalDate): TrnStatus =
        if (tradeDate.isAfter(dateUtils.date)) TrnStatus.PROPOSED else TrnStatus.SETTLED

    private fun toSplit(
        currentPosition: Position,
        corporateEvent: CorporateEvent
    ): TrnInput {
        val callerRef =
            CallerRef(
                corporateEvent.source,
                requireNotNull(corporateEvent.id) { "Corporate event must be saved before it becomes a transaction" }
            )
        return TrnInput(
            callerRef,
            currentPosition.asset.id,
            trnType = TrnType.SPLIT,
            quantity = corporateEvent.split,
            tradeDate = corporateEvent.recordDate,
            price = corporateEvent.split,
            status = statusOn(corporateEvent.recordDate),
            cashCurrency = currentPosition.asset.market.currency.code
        )
    }

    private fun toDividend(
        currentPosition: Position,
        corporateEvent: CorporateEvent,
        payDate: LocalDate
    ): TrnInput {
        // Always PROPOSED: the owner may take the dividend as shares in a reinvestment plan
        // (DRIP), which Beancounter does not record, so they choose which to keep as income.
        // svc-data's daily job settles one proposed before its pay date when that date arrives.
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
                requireNotNull(corporateEvent.id) { "Corporate event must be saved before it becomes a transaction" }
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