package com.beancounter.marketdata.tax

import java.math.BigDecimal

/**
 * Request to create or update a tax rate.
 */
data class TaxRateRequest(
    val countryCode: String,
    val rate: BigDecimal
)

/**
 * DTO for tax rate data.
 */
data class TaxRateDto(
    val countryCode: String,
    val rate: BigDecimal
)

/**
 * Response for a single tax rate.
 */
data class TaxRateResponse(
    val data: TaxRateDto
)

/**
 * Response for multiple tax rates.
 */
data class TaxRatesResponse(
    val data: List<TaxRateDto>
)