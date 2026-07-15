package com.beancounter.marketdata.providers.eodhd.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Response DTOs for the EODHD Fundamentals endpoint.
 *
 * `GET /api/fundamentals/{symbol}.{exchange}?filter=General,ETF_Data&api_token={key}&fmt=json`
 *
 * The same endpoint serves two shapes, dispatched on [EodhdGeneral.type]:
 * - Equities (`Common Stock`): scalar sector/industry live under `General`; there is no `ETF_Data`.
 * - ETFs (`ETF`): multi-sector exposure lives under `ETF_Data.Sector_Weights`; there is no
 *   `General.Sector`.
 *
 * Only the sector-classification fields are modelled; every other Fundamentals field is ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdFundamentals(
    @JsonProperty("General")
    val general: EodhdGeneral? = null,
    @JsonProperty("ETF_Data")
    val etfData: EodhdEtfData? = null
)

/**
 * `General` block — populated for equities (and carries the instrument [type] for both shapes).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdGeneral(
    @JsonProperty("Type")
    val type: String? = null,
    @JsonProperty("Sector")
    val sector: String? = null,
    @JsonProperty("Industry")
    val industry: String? = null
)

/**
 * `ETF_Data` block — populated for ETFs. Only sector weights are modelled.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdEtfData(
    /**
     * Keyed by EODHD (Morningstar-style) sector name, e.g. "Technology", "Financial Services".
     * Weights are Morningstar buckets, not GICS — [com.beancounter.marketdata.classification.SectorNormalizer]
     * maps them onto the canonical GICS names used across Beancounter.
     */
    @JsonProperty("Sector_Weights")
    val sectorWeights: Map<String, EodhdSectorWeight>? = null
)

/**
 * A single ETF sector weight. Values arrive as strings (e.g. "33.52294") — parse to BigDecimal.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdSectorWeight(
    @JsonProperty("Equity_%")
    val equityPct: String? = null,
    @JsonProperty("Relative_to_Category")
    val relativeToCategory: String? = null
)