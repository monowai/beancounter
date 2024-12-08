package com.beancounter.common

import com.beancounter.common.contracts.EventRequest
import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.input.TrustedEventInput
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Corporate Action Event pojo tests
 */
class TestEvent {
    @Test
    fun is_EventSerializingLocalDate() {
        val recordDateString = "2020-05-20"

        val eventJson = """{
    "id": "Random",
    "trnType": "DIVI",
    "source": "ALPHA",
    "assetId": "MSFT-US",
    "rate": 0.5100,
    "split": 1.000000,
    "payDate": "$recordDateString",
    "recordDate": "$recordDateString"
  }"""
        val (_, _, _, _, recordDate, _, _, payDate) =
            objectMapper.readValue<CorporateEvent>(
                eventJson,
            )
        assertThat(recordDate).isEqualTo(recordDateString)
        assertThat(payDate).isEqualTo(recordDateString)
    }

    @Test
    fun is_EventConstructingAndSerializing() {
        val event =
            CorporateEvent(
                id = null,
                trnType = TrnType.DIVI,
                source = "TEST",
                "assetId",
                rate = BigDecimal.TEN,
            )
        val eventRequest = EventRequest(event)
        val json = objectMapper.writeValueAsBytes(eventRequest)
        val fromJson = objectMapper.readValue<EventRequest>(json)
        assertThat(fromJson.data)
            .usingRecursiveComparison()
            .ignoringFields("id")
    }

    @Test
    fun is_EventInputSerializing() {
        val trustedEventInput =
            TrustedEventInput(
                CorporateEvent(
                    source = "test",
                    assetId = "xxx",
                    rate = BigDecimal.ONE,
                ),
            )
        val json = objectMapper.writeValueAsString(trustedEventInput)
        assertThat(
            objectMapper.readValue(
                json,
                TrustedEventInput::class.java,
            ),
        ).usingRecursiveComparison()
            .isEqualTo(trustedEventInput)
    }
}
