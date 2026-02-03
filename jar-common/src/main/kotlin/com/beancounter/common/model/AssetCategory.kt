package com.beancounter.common.model

/**
 * Simple classification structure of an Asset.
 */
data class AssetCategory(
    var id: String,
    var name: String
) {
    companion object {
        const val CASH: String = "CASH"
        const val RE: String = "RE"
        const val ACCOUNT: String = "ACCOUNT"
        const val TRADE: String = "TRADE"
        const val POLICY: String = "POLICY"

        @Deprecated("Use POLICY instead", replaceWith = ReplaceWith("POLICY"))
        const val PENSION: String = "PENSION"

        // Report category constants for higher-level grouping
        const val REPORT_CASH: String = "Cash"
        const val REPORT_EQUITY: String = "Equity"
        const val REPORT_ETF: String = "ETF"
        const val REPORT_MUTUAL_FUND: String = "Mutual Fund"
        const val REPORT_PROPERTY: String = "Property"
        const val REPORT_RETIREMENT_FUND: String = "Retirement Fund"

        /**
         * Maps a detailed category to a higher-level report category.
         * Used for backward compatibility when reportCategory is not explicitly set.
         */
        fun toReportCategory(category: String): String =
            when (category.uppercase()) {
                CASH, ACCOUNT, TRADE, "BANK ACCOUNT" -> REPORT_CASH
                "EQUITY" -> REPORT_EQUITY
                RE, "REAL ESTATE" -> REPORT_PROPERTY
                "EXCHANGE TRADED FUND", "ETF" -> REPORT_ETF
                "MUTUAL FUND" -> REPORT_MUTUAL_FUND
                POLICY, PENSION -> REPORT_RETIREMENT_FUND
                else -> category // Default: use original category
            }
    }
}