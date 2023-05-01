package com.beancounter.common.model

/**
 * Constants that determine the enums foo a type of Transaction.
 *
 * @author mikeh
 * @since 2019-02-07
 */
enum class TrnType {
    SELL, // -ve Trade, +ve Cash
    BUY, // +ve Trade, -ve Cash
    SPLIT,
    DEPOSIT, // +ve Cash Impact
    WITHDRAWAL, // -ve Cash Impact
    DIVI,
    FX_BUY, // FX between Trade and Cash
    IGNORE,
    INCREASE, // +ve impact on MV. No cash impact.
    REDUCE, // -ve impact on MV. No cash impact.
    ;

    companion object {

        val creditsCash = arrayOf(DEPOSIT, SELL, DIVI)
        val debitsCash = arrayOf(BUY, WITHDRAWAL, FX_BUY)

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
