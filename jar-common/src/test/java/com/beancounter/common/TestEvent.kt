package com.beancounter.common

import com.beancounter.common.contracts.EventRequest
import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.BcJson.objectMapper
import com.beancounter.common.utils.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class TestEvent {
    @Test
    @Throws(Exception::class)
    fun is_EventSerializingLocalDate() {
        val eventJson = """{
    "id": "Random",
    "trnType": "DIVI",
    "source": "ALPHA",
    "assetId": "MSFT-US",
    "rate": 0.5100,
    "split": 1.000000,
    "payDate": "2020-05-20",
    "recordDate": "2020-05-20"
  }"""
        val (_, _, _, _, recordDate, _, _, payDate) = objectMapper.readValue(eventJson, CorporateEvent::class.java)
        assertThat(recordDate).isEqualTo("2020-05-20")
        assertThat(payDate).isEqualTo("2020-05-20")
    }

    @Test
    fun is_EventConstructingAndSerializing() {
        val event = CorporateEvent(TrnType.DIVI, "TEST", "assetId", DateUtils().date!!, BigDecimal.TEN)
        val eventRequest = EventRequest(event)
        val json = objectMapper.writeValueAsBytes(eventRequest)
        val fromJson = objectMapper.readValue(json, EventRequest::class.java)
        assertThat(fromJson.data).usingRecursiveComparison().ignoringFields("id")
    }
}
