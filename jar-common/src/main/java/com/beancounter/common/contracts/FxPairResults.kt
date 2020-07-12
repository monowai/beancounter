package com.beancounter.common.contracts

import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.utils.CurrencyKeyDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

data class FxPairResults constructor(
        @JsonDeserialize(keyUsing = CurrencyKeyDeserializer::class)
        var rates: Map<IsoCurrencyPair, FxRate> = HashMap()
)