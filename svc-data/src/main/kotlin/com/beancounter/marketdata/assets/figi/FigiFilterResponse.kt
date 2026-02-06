package com.beancounter.marketdata.assets.figi

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Response from OpenFIGI /v3/search endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class FigiFilterResponse(
    val data: List<FigiFilterResult> = emptyList(),
    val next: String? = null
)

/**
 * Individual result from FIGI search.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class FigiFilterResult(
    val figi: String? = null,
    val name: String? = null,
    val ticker: String? = null,
    val exchCode: String? = null,
    val compositeFIGI: String? = null,
    val securityType: String? = null,
    val marketSector: String? = null,
    val securityType2: String? = null,
    val securityDescription: String? = null
)