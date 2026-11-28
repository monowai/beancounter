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
    BALANCE, // absolute impact on MV. No cash impact.
    ADD, // Same as buy but does not impact cash.
    INCOME, // +ve Cash Impact (interest, salary, etc.)
    DEDUCTION, // -ve Cash Impact (fees, charges, etc.)
    REDUCE, // Same as sell but does not impact cash.
    COST_ADJUST, // Adjusts cost basis only. No quantity or cash impact.
    EXPENSE // -ve Cash Impact for asset-related expenses (maintenance, taxes, etc.)
    ;

    /**
     * A trn has a trade date and no time of day. Trns of one trade date apply in this order:
     * buys and adds, then the split, then the rest, and last a balance, which states the
     * position the day closed on.
     */
    val sameDayOrder: Int
        get() =
            when (this) {
                BUY, ADD -> 0
                SPLIT -> 1
                BALANCE -> AFTER_THE_REST
                else -> 2
            }

    companion object {
        private const val AFTER_THE_REST = 3

        /** Trade date order, and [sameDayOrder] within a trade date. The sort is stable. */
        val inTradeOrder: Comparator<Trn> = compareBy({ it.tradeDate }, { it.trnType.sameDayOrder })

        val creditsCash =
            arrayOf(
                DEPOSIT,
                SELL,
                DIVI,
                INCOME
            )
        val debitsCash =
            arrayOf(
                BUY,
                WITHDRAWAL,
                FX_BUY,
                DEDUCTION,
                EXPENSE
            )

        @JvmStatic
        fun isCashImpacted(trnType: TrnType): Boolean =
            (
                trnType != SPLIT &&
                    trnType != ADD &&
                    trnType != REDUCE &&
                    trnType != BALANCE &&
                    trnType != COST_ADJUST
            )

        @JvmStatic
        fun isCash(trnType: TrnType): Boolean =
            (
                trnType == DEPOSIT ||
                    trnType == WITHDRAWAL ||
                    trnType == DEDUCTION
            )

        @JvmStatic
        fun isCashCredited(trnType: TrnType): Boolean = creditsCash.contains(trnType)

        @JvmStatic
        fun isCashDebited(trnType: TrnType): Boolean = debitsCash.contains(trnType)

        /**
         * Determines if a transaction type builds/enters a position.
         * Used to detect true position re-entry after a sell-out, vs just receiving
         * dividends or other non-position-building events.
         */
        @JvmStatic
        fun isPositionBuilding(trnType: TrnType): Boolean = trnType == BUY || trnType == ADD
    }
}