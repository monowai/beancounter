package com.beancounter.marketdata.assets.figi

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Request parameters for OpenFIGI /v3/search endpoint.
 * Supports keyword search with optional exchange filtering.
 * When securityType2 is null, all security types are returned.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class FigiFilterRequest(
    val query: String,
    val exchCode: String? = null,
    val securityType2: String? = null
)