package com.beancounter.marketdata.assets.figi

/**
 * This is BB OpenFigi asset representation.
 */
data class FigiAsset(
    val name: String,
    val ticker: String,
    val securityType2: String
)