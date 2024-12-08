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
        fun deposit(
            cashAsset: Asset,
            balance: BigDecimal,
            portfolio: Portfolio,
            tradeBaseRate: BigDecimal = BigDecimal.ONE,
            tradePortfolioRate: BigDecimal = BigDecimal.ONE,
        ): Trn =
            Trn(
                trnType = TrnType.DEPOSIT,
                portfolio = portfolio,
                asset = cashAsset,
                price = BigDecimal.ONE,
                // This is the resolved cash asset that import will figure out from CashCurrency
                cashAsset = cashAsset,
                quantity = balance,
                tradeBaseRate = tradeBaseRate,
                tradePortfolioRate = tradePortfolioRate,
            )

        @JvmStatic
        fun convert(
            credit: Asset,
            creditAmount: BigDecimal,
            debit: Asset,
            debitAmount: BigDecimal,
            portfolio: Portfolio,
            tradeBaseRate: BigDecimal = BigDecimal.ONE,
            tradePortfolioRate: BigDecimal = BigDecimal.ONE,
        ): Trn =
            Trn(
                trnType = TrnType.FX_BUY,
                portfolio = portfolio,
                asset = credit,
                cashAsset = debit,
                price = BigDecimal.ONE,
                tradeBaseRate = tradeBaseRate,
                tradePortfolioRate = tradePortfolioRate,
                // Amount to receive
                quantity = creditAmount,
                // Amount to debit
                cashAmount = debitAmount.multiply(BigDecimal("-1")),
            )
    }
}
