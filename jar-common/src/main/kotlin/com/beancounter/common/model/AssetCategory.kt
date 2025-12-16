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
    }
}