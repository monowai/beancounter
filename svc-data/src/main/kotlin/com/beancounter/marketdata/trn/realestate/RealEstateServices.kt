package com.beancounter.marketdata.trn.realestate

import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.MathUtils
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.currency.CurrencyService
import java.math.BigDecimal

class RealEstateServices(val assetService: AssetService, val currencyService: CurrencyService) {
    fun getImpact(trnInput: TrnInput, tradeAmount: BigDecimal = trnInput.tradeAmount): BigDecimal {
        if (TrnType.isCashImpacted(trnInput.trnType)) {
            if (trnInput.cashAmount.compareTo(BigDecimal.ZERO) != 0) {
                return trnInput.cashAmount // Cash amount has been set by the caller
            }
            val rate = trnInput.tradeCashRate ?: BigDecimal("1.00")
            if (TrnType.creditsCash.contains(trnInput.trnType)) {
                return MathUtils.divide(tradeAmount.abs(), rate)
            } else if (TrnType.debitsCash.contains(trnInput.trnType)) {
                return MathUtils.divide(BigDecimal.ZERO.minus(tradeAmount.abs()), rate)
            }
        }
        return BigDecimal.ZERO
    }
}
