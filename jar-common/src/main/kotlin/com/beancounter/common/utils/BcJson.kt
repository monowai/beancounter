package com.beancounter.common.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

/**
 * Kotlin aware Jackson Object mapper.
 */
class BcJson {
    companion object {
        @JvmStatic
        val objectMapper: ObjectMapper =
            ObjectMapper()
                .registerKotlinModule()
                .registerModule(JavaTimeModule())

        @JvmStatic
        val writer: ObjectWriter = objectMapper.writerWithDefaultPrettyPrinter()
    }
}