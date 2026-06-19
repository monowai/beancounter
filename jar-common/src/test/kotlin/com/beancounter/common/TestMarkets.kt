package com.beancounter.common

import com.beancounter.common.contracts.MarketResponse
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import tools.jackson.databind.ObjectMapper
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.module.kotlin.KotlinModule

internal class TestMarkets {
    private var yamlMapper: ObjectMapper =
        YAMLMapper.builder().addModule(KotlinModule.Builder().build()).build()

    @Test
    @Throws(Exception::class)
    fun is_MarketResponseSerializing() {
        val nasdaq = Market("NASDAQ")
        val nzx =
            Market(
                "NZX",
                NZD.code
            )
        val markets: MutableCollection<Market> = ArrayList()
        markets.add(nasdaq)
        markets.add(nzx)
        val marketResponse = MarketResponse(markets)
        assertThat(marketResponse.data).hasSize(2)
        val json = objectMapper.writeValueAsString(marketResponse)
        val (data) =
            objectMapper.readValue(
                json,
                MarketResponse::class.java
            )
        assertThat(data).containsExactly(
            nasdaq,
            nzx
        )
    }

    @Test
    @Throws(Exception::class)
    fun fromJson() {
        val file = ClassPathResource("application-markets.yml").file
        val marketResponse =
            yamlMapper.readValue(
                file,
                MarketResponse::class.java
            )
        assertThat(marketResponse).isNotNull
        assertThat(marketResponse.data).hasSize(2)
    }

    companion object {
        val USD = Currency("USD")
        val NZD = Currency("NZD")
    }
}