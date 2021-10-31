package com.beancounter.marketdata.trn

import com.beancounter.common.model.Trn
import com.beancounter.common.utils.DateUtils
import org.springframework.stereotype.Service

/**
 * Export and Import format of a BC transaction.
 */
@Service
class TrnIoDefinition {
    val dateUtils = DateUtils()

    /**
     * TRN columns that will be read when importing a delimited file.
     */
    enum class Columns {
        Provider, Batch, CallerId, Type, Market, Code, Name, Date,
        Quantity, BaseCurrency, BaseRate, TradeCurrency, Price, Fees, PortfolioRate, TradeAmount, Comments
    }

    fun headers(): List<String> {
        return Columns::class.java.enumConstants.map(Enum<*>::name)
    }

    fun toArray(trn: Trn): Array<String?> {
        return arrayOf(
            trn.callerRef?.provider,
            trn.callerRef?.batch,
            trn.callerRef?.callerId,
            trn.trnType.name,
            trn.asset.market.code,
            trn.asset.code,
            trn.asset.name,
            trn.tradeDate.toString(),
            trn.quantity.toString(),
            trn.portfolio.base.code,
            trn.tradeBaseRate.toString(),
            trn.tradeCurrency.code,
            trn.price.toString(),
            trn.fees.toString(),
            trn.tradePortfolioRate.toString(),
            trn.tradeAmount.toString(),
            trn.comments
        )
    }

    companion object {
        @JvmStatic
        fun colDef() = mapOf(
            Pair(Columns.Provider, 0),
            Pair(Columns.Batch, 1),
            Pair(Columns.CallerId, 2),
            Pair(Columns.Type, 3),
            Pair(Columns.Market, 4),
            Pair(Columns.Code, 5),
            Pair(Columns.Name, 6),
            Pair(Columns.Date, 7),
            Pair(Columns.Quantity, 8),
            Pair(Columns.BaseCurrency, 9),
            Pair(Columns.BaseRate, 10),
            Pair(Columns.TradeCurrency, 11),
            Pair(Columns.Price, 12),
            Pair(Columns.Fees, 13),
            Pair(Columns.PortfolioRate, 14),
            Pair(Columns.TradeAmount, 15),
            Pair(Columns.Comments, 16),
        )
    }
}
