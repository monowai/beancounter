package com.beancounter.common

import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.utils.BcJson.objectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

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
        Assertions.assertThat(recordDate).isEqualTo("2020-05-20")
        Assertions.assertThat(payDate).isEqualTo("2020-05-20")
    }
}