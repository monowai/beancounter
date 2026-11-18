package com.beancounter.marketdata.trn

import com.beancounter.common.model.Broker
import com.beancounter.common.model.TrnType
import java.time.LocalDate

/**
 * The broker a corporate event belongs to, when that is unambiguous. svc-event knows the
 * portfolio and asset but not the broker, so its dividends and splits arrive with none. When
 * every settled trade building the portfolio's holding of the asset, up to the event's date,
 * was at one broker, the event is that broker's; otherwise the owner chooses.
 */
object SoleBroker {
    private val HOLDING_TYPES = listOf(TrnType.BUY, TrnType.ADD, TrnType.SELL, TrnType.REDUCE)
    private val EVENT_TYPES = setOf(TrnType.DIVI, TrnType.SPLIT)

    /** Whether [type] is an event that arrives without a broker. */
    fun isEvent(type: TrnType): Boolean = type in EVENT_TYPES

    fun of(
        trnRepository: TrnRepository,
        portfolioId: String,
        assetId: String,
        date: LocalDate
    ): Broker? {
        val sole = trnRepository.findHoldingBrokers(portfolioId, assetId, date, HOLDING_TYPES).singleOrNull()
        return sole?.takeUnless { trnRepository.hasUnbrokeredHolding(portfolioId, assetId, date, HOLDING_TYPES) }
    }
}