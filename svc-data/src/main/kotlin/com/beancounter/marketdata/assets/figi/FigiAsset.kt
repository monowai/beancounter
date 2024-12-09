package com.beancounter.marketdata.assets.figi

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * This is BB OpenFigi asset representation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class FigiAsset(
    val name: String,
    val ticker: String,
    val securityType2: String
)