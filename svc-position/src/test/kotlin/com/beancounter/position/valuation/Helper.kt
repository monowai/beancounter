package com.beancounter.position.valuation

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import java.math.BigDecimal

/**
 * general positional transaction helpers. Refactor to svc-data/testFixtures?
 */
class Helper {
    companion object {
        /**
         * Helpers to build transactions without having to use wiremock.
         */
        @JvmStatic
        fun getDeposit(cashAsset: Asset, balance: BigDecimal, portfolio: Portfolio): Trn {
            return Trn(
                trnType = TrnType.DEPOSIT,
                portfolio = portfolio,
                asset = cashAsset,
                price = BigDecimal.ONE,
                cashAsset = cashAsset, // This is the resolved cash asset that import will figure out from CashCurrency
                quantity = balance,
            )
        }

        @JvmStatic
        fun convert(
            credit: Asset,
            creditAmount: BigDecimal,
            debit: Asset,
            debitAmount: BigDecimal,
            portfolio: Portfolio,
        ): Trn {
            return Trn(
                trnType = TrnType.FX_BUY,
                portfolio = portfolio,
                asset = credit,
                cashAsset = debit,
                price = BigDecimal.ONE,
                quantity = creditAmount, // Amount to receive
                cashAmount = debitAmount.multiply(BigDecimal("-1")), // Amount to debit
            )
        }
    }
}
