package com.beancounter.common

import com.beancounter.common.contracts.Payload
import com.beancounter.common.contracts.PositionRequest
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.model.Positions
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class TestPositionBuckets {
    private val mapper = BcJson().objectMapper

    @Test
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
