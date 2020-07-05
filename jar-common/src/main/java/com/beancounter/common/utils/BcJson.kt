package com.beancounter.common.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.module.kotlin.KotlinModule

object BcJson {
    @JvmStatic
    val objectMapper: ObjectMapper = ObjectMapper()
            .registerModule(KotlinModule())

    @JvmStatic
    val writer: ObjectWriter = objectMapper.writerWithDefaultPrettyPrinter()

}