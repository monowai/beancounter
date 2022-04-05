package com.beancounter.common

import com.beancounter.common.contracts.MarketResponse
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.utils.BcJson
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

internal class TestMarkets {
    var jsonMapper = BcJson().objectMapper
    var yamlMapper: ObjectMapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule.Builder().build())

    @Test
    @Throws(Exception::class)
    fun is_MarketResponseSerializing() {
        val nasdaq = Market("NASDAQ", USD)
        val nzx = Market("NZX", NZD)
        val markets: MutableCollection<Market> = ArrayList()
        markets.add(nasdaq)
        markets.add(nzx)
        val marketResponse = MarketResponse(markets)
        assertThat(marketResponse.data).hasSize(2)
        val json = jsonMapper.writeValueAsString(marketResponse)
        val (data) = jsonMapper.readValue(json, MarketResponse::class.java)
        assertThat(data).containsExactly(nasdaq, nzx)
    }

    @Test
    @Throws(Exception::class)
    fun fromJson() {
        val file = ClassPathResource("application-markets.yml").file
        val marketResponse = yamlMapper.readValue(file, MarketResponse::class.java)
        assertThat(marketResponse).isNotNull
        assertThat(marketResponse.data).hasSize(2)
    }

    companion object {
        val USD = Currency("USD")
        val NZD = Currency("NZD")
    }
}
