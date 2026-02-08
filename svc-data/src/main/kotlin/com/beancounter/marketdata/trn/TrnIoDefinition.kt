package com.beancounter.marketdata.trn

import com.beancounter.common.model.Trn
import com.beancounter.common.utils.DateUtils
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service

/**
 * Export and Import format of a BC transaction.
 */
@Service
class TrnIoDefinition(
    val dateUtils: DateUtils,
    val objectMapper: ObjectMapper = ObjectMapper()
) {
    /**
     * TRN columns that will be read when importing a delimited file.
     */
    enum class Columns {
        Batch,
        CallerId,
        Type,
        Market,
        Code,
        Name,
        CashAccount,
        CashCurrency,
        Date,
        Quantity,
        BaseRate,
        TradeCurrency,
        Price,
        Fees,
        PortfolioRate,
        TradeAmount,
        CashAmount,
        Comments,
        Status,
        Broker,
        SubAccounts
    }

    fun headers(): Array<String> =
        Columns::class.java.enumConstants
            .map(Enum<*>::name)
            .toTypedArray()

    fun export(trn: Trn): Array<String?> {
        val map = buildTrnExportMap(trn)
        return map.values.toTypedArray()
    }

    private fun buildTrnExportMap(trn: Trn): Map<Int, String?> =
        mapOf(
            Pair(Columns.Batch.ordinal, trn.callerRef?.batch),
            Pair(Columns.CallerId.ordinal, trn.callerRef?.callerId),
            Pair(Columns.Type.ordinal, trn.trnType.name),
            Pair(Columns.Market.ordinal, trn.asset.market.code),
            Pair(Columns.Code.ordinal, trn.asset.code),
            Pair(Columns.Name.ordinal, trn.asset.name),
            Pair(Columns.CashAccount.ordinal, trn.cashAsset?.id),
            Pair(Columns.CashCurrency.ordinal, trn.cashAsset?.priceSymbol),
            Pair(Columns.Date.ordinal, trn.tradeDate.toString()),
            Pair(Columns.Quantity.ordinal, trn.quantity.toString()),
            Pair(Columns.BaseRate.ordinal, trn.tradeBaseRate.toString()),
            Pair(Columns.TradeCurrency.ordinal, trn.tradeCurrency.code),
            Pair(Columns.Price.ordinal, trn.price.toString()),
            Pair(Columns.Fees.ordinal, trn.fees.toString()),
            Pair(Columns.PortfolioRate.ordinal, trn.tradePortfolioRate.toString()),
            Pair(Columns.TradeAmount.ordinal, trn.tradeAmount.toString()),
            Pair(Columns.CashAmount.ordinal, trn.cashAmount.toString()),
            Pair(Columns.Comments.ordinal, trn.comments),
            Pair(Columns.Status.ordinal, trn.status.name),
            Pair(Columns.Broker.ordinal, trn.broker?.id),
            Pair(
                Columns.SubAccounts.ordinal,
                trn.subAccounts?.let { objectMapper.writeValueAsString(it) }
            )
        )
}