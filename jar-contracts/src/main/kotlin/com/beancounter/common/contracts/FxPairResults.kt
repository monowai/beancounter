package com.beancounter.common.contracts

import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.utils.CurrencyKeyDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

/**
 * FX Rates found for requested currency pairs.
 */
data class FxPairResults(
    @JsonDeserialize(keyUsing = CurrencyKeyDeserializer::class)
    var rates: Map<IsoCurrencyPair, FxRate> = mapOf()
)