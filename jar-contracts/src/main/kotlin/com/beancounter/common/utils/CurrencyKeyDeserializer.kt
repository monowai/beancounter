package com.beancounter.common.utils

import com.beancounter.common.model.IsoCurrencyPair
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.KeyDeserializer

private const val CODE_LENGTH = 3
private const val TO_CODE_START = CODE_LENGTH + 1
private const val TO_CODE_END = TO_CODE_START + CODE_LENGTH

/**
 * Convert the String into an Object representation. Jackson
 */
class CurrencyKeyDeserializer : KeyDeserializer() {
    override fun deserializeKey(
        key: String,
        ctxt: DeserializationContext
    ): IsoCurrencyPair =
        IsoCurrencyPair(
            key.substring(
                0,
                CODE_LENGTH
            ),
            key.substring(
                TO_CODE_START,
                TO_CODE_END
            )
        )
}