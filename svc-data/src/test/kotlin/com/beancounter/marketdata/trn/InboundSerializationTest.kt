package com.beancounter.marketdata.trn

import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.marketdata.utils.RegistrationUtils.objectMapper
import com.fasterxml.jackson.core.type.TypeReference
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

/**
 * Serialization test to support import requests.
 */
class InboundSerializationTest {
    @Test
    @Throws(Exception::class)
    fun is_InboundPayloadConverted() {
        val payload = objectMapper.readValue(
            ClassPathResource("/kafka/bc-view-send.json").file,
            TrustedTrnImportRequest::class.java
        )
        assertThat(payload).isNotNull
        val message: HashMap<String, Any> = objectMapper.readValue(
            ClassPathResource("/kafka/csv-import-message.json").file,
            object : TypeReference<HashMap<String, Any>>() {}
        )
        assertThat(message).hasFieldOrProperty("payload")
        val (portfolio, _, callerRef, _, row) = objectMapper.readValue(
            message["payload"].toString(),
            TrustedTrnImportRequest::class.java
        )
        assertThat(portfolio).usingRecursiveComparison().isEqualTo(payload.portfolio)
        for (column in row) {
            assertThat(payload.row).contains(column)
        }
        assertThat(callerRef).isNotNull
    }

    @Test
    @Throws(Exception::class)
    fun is_InboundMessagePayloadConverted() {
        val payload = objectMapper.readValue(
            ClassPathResource("/kafka/bc-view-message.json").file,
            TrustedTrnImportRequest::class.java
        )
        assertThat(payload).isNotNull
    }

    @Test
    @Throws(Exception::class)
    fun is_IncomingTrustedEvent() {
        val inbound = objectMapper.readValue(
            ClassPathResource("/kafka/event-incoming.json").file, TrustedTrnEvent::class.java
        )
        assertThat(inbound).isNotNull
    }
}
