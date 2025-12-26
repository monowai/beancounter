package com.beancounter.marketdata.providers.alpha

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Response DTO for AlphaVantage OVERVIEW endpoint.
 * Contains company sector and industry information.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphaOverviewResponse(
    @JsonProperty("Symbol")
    val symbol: String? = null,
    @JsonProperty("AssetType")
    val assetType: String? = null,
    @JsonProperty("Name")
    val name: String? = null,
    @JsonProperty("Sector")
    val sector: String? = null,
    @JsonProperty("Industry")
    val industry: String? = null,
    @JsonProperty("Exchange")
    val exchange: String? = null,
    @JsonProperty("Currency")
    val currency: String? = null,
    @JsonProperty("Country")
    val country: String? = null
)

/**
 * Response DTO for AlphaVantage ETF_PROFILE endpoint.
 * Contains sector allocations and holdings.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphaEtfProfileResponse(
    val sectors: List<AlphaEtfSector>? = null,
    val holdings: List<AlphaEtfHolding>? = null,
    @JsonProperty("net_assets")
    val netAssets: String? = null,
    @JsonProperty("net_expense_ratio")
    val netExpenseRatio: String? = null
)

/**
 * Sector allocation within an ETF.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphaEtfSector(
    val sector: String,
    val weight: String
)

/**
 * Individual holding within an ETF.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphaEtfHolding(
    val symbol: String? = null,
    val description: String? = null,
    val weight: String? = null
)