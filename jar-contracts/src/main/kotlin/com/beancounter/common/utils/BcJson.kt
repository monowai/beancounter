package com.beancounter.common.utils

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.ObjectWriter
import tools.jackson.module.kotlin.jacksonMapperBuilder

/**
 * Kotlin aware Jackson Object mapper.
 */
class BcJson {
    companion object {
        @JvmStatic
        val objectMapper: ObjectMapper =
            jacksonMapperBuilder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build()

        @JvmStatic
        val writer: ObjectWriter = objectMapper.writerWithDefaultPrettyPrinter()
    }
}
