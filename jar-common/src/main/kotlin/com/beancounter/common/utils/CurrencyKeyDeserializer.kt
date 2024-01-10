package com.beancounter.common.utils

import com.beancounter.common.model.IsoCurrencyPair
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.KeyDeserializer

/**
 * Convert the String into an Object representation. Jackson
 */
class CurrencyKeyDeserializer : KeyDeserializer() {
    override fun deserializeKey(
        key: String,
        ctxt: DeserializationContext,
    ): IsoCurrencyPair {
        return IsoCurrencyPair(key.substring(0, 3), key.substring(4, 7))
    }
}
