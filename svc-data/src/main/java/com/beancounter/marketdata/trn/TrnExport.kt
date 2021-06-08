package com.beancounter.marketdata.trn

import com.beancounter.common.model.Trn
import com.beancounter.common.utils.DateUtils
import org.springframework.stereotype.Service

/**
 * Export format of a BC transaction.
 */
@Service
class TrnExport {
    val dateUtils = DateUtils()
    fun headers(): Array<String> {
        return arrayOf(
            "provider",
            "batch",
            "callerId",
            "type",
            "market",
            "code",
            "name",
            "date",
            "quantity",
            "tradeCurrency",
            "price",
            "fees",
            "portfolioRate",
            "tradeAmount",
            "comments"
        )
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
            dateUtils.getDateString(trn.tradeDate),
            trn.quantity.toString(),
            trn.tradeCurrency.code,
            trn.price.toString(),
            trn.fees.toString(),
            trn.tradePortfolioRate.toString(),
            trn.tradeAmount.toString(),
            trn.comments
        )
    }
}
