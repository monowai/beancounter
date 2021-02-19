package com.beancounter.common.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.stereotype.Service

@Service
class BcJson {
    final val objectMapper: ObjectMapper = ObjectMapper().registerKotlinModule()

    val writer: ObjectWriter = objectMapper.writerWithDefaultPrettyPrinter()

}