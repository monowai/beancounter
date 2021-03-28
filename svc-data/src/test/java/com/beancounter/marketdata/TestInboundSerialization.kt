package com.beancounter.marketdata

import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.utils.BcJson
import com.fasterxml.jackson.core.type.TypeReference
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import java.util.HashMap

class TestInboundSerialization {
    private val objectMapper = BcJson().objectMapper
    @Test
    @Throws(Exception::class)
    fun is_InboundPayloadConverted() {
        val payload = objectMapper.readValue(
            ClassPathResource("/kafka/bc-view-send.json").file,
            TrustedTrnImportRequest::class.java
        )
        Assertions.assertThat(payload).isNotNull
        val message: HashMap<String, Any> = objectMapper.readValue(
            ClassPathResource("/kafka/csv-import-message.json").file,
            object : TypeReference<HashMap<String, Any>>() {}
        )
        Assertions.assertThat(message).hasFieldOrProperty("payload")
        val (portfolio, _, callerRef, _, row) = objectMapper.readValue(
            message["payload"].toString(),
            TrustedTrnImportRequest::class.java
        )
        Assertions.assertThat(portfolio).usingRecursiveComparison().isEqualTo(payload.portfolio)
        Assertions.assertThat(row).contains(*payload.row.toTypedArray())
        Assertions.assertThat(callerRef).isNotNull
    }

    @Test
    @Throws(Exception::class)
    fun is_InboundMessagePayloadConverted() {
        val payload = objectMapper.readValue(
            ClassPathResource("/kafka/bc-view-message.json").file,
            TrustedTrnImportRequest::class.java
        )
        Assertions.assertThat(payload).isNotNull
    }

    @Test
    @Throws(Exception::class)
    fun is_IncomingTrustedEvent() {
        val inbound = objectMapper.readValue(
            ClassPathResource("/kafka/event-incoming.json").file, TrustedTrnEvent::class.java
        )
        Assertions.assertThat(inbound).isNotNull
    }
}
