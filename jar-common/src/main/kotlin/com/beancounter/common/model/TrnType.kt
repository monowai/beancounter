package com.beancounter.common.model

/**
 * Constants that determine the enums foo a type of Transaction.
 *
 * @author mikeh
 * @since 2019-02-07
 */
enum class TrnType {
    SELL, BUY, SPLIT, DEPOSIT, WITHDRAWAL, DIVI, FX_BUY, IGNORE;

    companion object {

        val creditsCash = arrayOf(DEPOSIT, SELL, DIVI)
        val debitsCash = arrayOf(BUY, WITHDRAWAL, FX_BUY)

        @JvmStatic
        fun isCorporateAction(trnType: TrnType): Boolean {
            return (DIVI == trnType || SPLIT == trnType)
        }

        @JvmStatic
        fun isCashImpacted(trnType: TrnType): Boolean {
            return (trnType != SPLIT)
        }

        @JvmStatic
        fun isCash(trnType: TrnType): Boolean {
            return (trnType == DEPOSIT || trnType == WITHDRAWAL)
        }

        @JvmStatic
        fun isCashCredited(trnType: TrnType): Boolean {
            return creditsCash.contains(trnType)
        }

        @JvmStatic
        fun isCashDebited(trnType: TrnType): Boolean {
            return debitsCash.contains(trnType)
        }
    }
}
