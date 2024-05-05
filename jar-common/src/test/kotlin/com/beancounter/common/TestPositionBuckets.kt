package com.beancounter.common

import com.beancounter.common.contracts.Payload
import com.beancounter.common.contracts.PositionRequest
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.model.Positions
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class TestPositionBuckets {
    @Test
    fun is_PositionSerializing() {
        val positionRequest = PositionRequest("ABC", ArrayList())
        var json: String = objectMapper.writeValueAsString(positionRequest)
        assertThat(objectMapper.readValue(json, PositionRequest::class.java))
            .usingRecursiveComparison().isEqualTo(positionRequest)
        val positionResponse = PositionResponse(Positions(getPortfolio()))
        json = objectMapper.writeValueAsString(positionResponse)
        assertThat(objectMapper.readValue(json, PositionResponse::class.java))
            .isNotNull
            .hasFieldOrProperty(Payload.DATA)
    }
}
