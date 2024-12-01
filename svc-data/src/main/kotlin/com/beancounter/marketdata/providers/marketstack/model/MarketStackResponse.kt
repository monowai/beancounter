package com.beancounter.marketdata.providers.marketstack.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Encapsulates the responses from the MarketDataProvider.
 *
 * @author mikeh
 * @since 2019-03-12
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MarketStackResponse(
    val data: List<MarketStackData> = listOf(),
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val error: MarketStackError? = null,
)
