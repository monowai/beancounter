package com.beancounter.marketdata.assets.figi

/**
 * Request parameters for OpenFIGI /v3/filter endpoint.
 * Supports keyword search with optional exchange filtering.
 */
data class FigiFilterRequest(
    val query: String,
    val exchCode: String? = null,
    val securityType2: String = "Common Stock"
)