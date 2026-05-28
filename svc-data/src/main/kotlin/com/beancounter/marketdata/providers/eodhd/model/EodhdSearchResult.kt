package com.beancounter.marketdata.providers.eodhd.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * EODHD search result row.
 *
 * Endpoint: GET /api/search/{query}?api_token={key}&fmt=json
 * Returns an array of these objects.
 *
 * Field casing in the EODHD response is PascalCase; map explicitly so Kotlin property names stay
 * idiomatic without relying on a global Jackson naming strategy.
 */
data class EodhdSearchResult(
    @param:JsonProperty("Code")
    val code: String,
    @param:JsonProperty("Exchange")
    val exchange: String,
    @param:JsonProperty("Name")
    val name: String,
    @param:JsonProperty("Type")
    val type: String? = null,
    @param:JsonProperty("Country")
    val country: String? = null,
    @param:JsonProperty("Currency")
    val currency: String? = null,
    @param:JsonProperty("ISIN")
    val isin: String? = null
)