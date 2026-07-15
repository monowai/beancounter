package com.beancounter.marketdata.trn

import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Build a WITHDRAWAL/DEPOSIT cash-leg [TrnInput] — the shape shared by
 * [com.beancounter.marketdata.cash.CashAutoSettleService],
 * [com.beancounter.marketdata.cash.CashTransferService], and
 * [PositionMoveService]'s compensating-cash transactions.
 *
 * Cash-leg invariants: the leg's asset settles to itself
 * (`assetId == cashAssetId`), and its trade currency and cash currency are
 * identical (`tradeCurrency == cashCurrency == [currency]`) — a cash leg
 * never carries an FX conversion of its own.
 */
fun cashLegInput(
    cashAssetId: String,
    trnType: TrnType,
    amount: BigDecimal,
    currency: String,
    tradeDate: LocalDate,
    status: TrnStatus,
    comments: String?,
    callerRef: CallerRef = CallerRef(),
    price: BigDecimal = BigDecimal.ONE,
    tradeCashRate: BigDecimal? = null
): TrnInput {
    val trnInput =
        TrnInput(
            callerRef = callerRef,
            assetId = cashAssetId,
            cashAssetId = cashAssetId,
            trnType = trnType,
            tradeAmount = amount,
            tradeCurrency = currency,
            cashCurrency = currency,
            tradeDate = tradeDate,
            status = status,
            comments = comments,
            price = price
        )
    if (tradeCashRate != null) {
        trnInput.tradeCashRate = tradeCashRate
    }
    return trnInput
}