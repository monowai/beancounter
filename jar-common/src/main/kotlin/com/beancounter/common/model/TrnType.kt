package com.beancounter.common.model

/**
 * Constants that determine the enums foo a type of Transaction.
 *
 * @author mikeh
 * @since 2019-02-07
 */
enum class TrnType {
    SELL, BUY, SPLIT, DEPOSIT, WITHDRAWAL, DIVI;

    companion object {
        @JvmStatic
        fun isCorporateAction(trnType: TrnType): Boolean {
            return (DIVI == trnType || SPLIT == trnType)
        }

        @JvmStatic
        fun isCashImpacted(trnType: TrnType): Boolean {
            return (trnType != SPLIT)
        }
    }
}
