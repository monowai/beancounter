package com.beancounter.common

import com.beancounter.common.TestMarkets.Companion.USD
import com.beancounter.common.contracts.Payload
import com.beancounter.common.contracts.PositionRequest
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Positions
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.fasterxml.jackson.core.JsonProcessingException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class TestPositionBuckets {
    private val mapper = BcJson().objectMapper

    @Test
    fun is_DefaultMoneyValuesSet() {
        val moneyValues = MoneyValues(USD)
        assertThat(moneyValues).hasNoNullFieldsOrPropertiesExcept("priceData", "weight")
    }

    @Test
    @Throws(JsonProcessingException::class)
    fun is_PositionSerializing() {
        val positionRequest = PositionRequest("ABC", ArrayList())
        var json: String = mapper.writeValueAsString(positionRequest)
        assertThat(mapper.readValue(json, PositionRequest::class.java))
            .usingRecursiveComparison().isEqualTo(positionRequest)
        val positionResponse = PositionResponse(Positions(getPortfolio()))
        json = mapper.writeValueAsString(positionResponse)
        assertThat(mapper.readValue(json, PositionResponse::class.java))
            .isNotNull
            .hasFieldOrProperty(Payload.DATA)
    }
}
