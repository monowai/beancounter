package com.beancounter.common

import com.beancounter.common.contracts.*
import com.beancounter.common.contracts.PriceRequest.Companion.of
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.AssetUtils.Companion.fromKey
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.AssetUtils.Companion.getAssetInput
import com.beancounter.common.utils.AssetUtils.Companion.getJsonAsset
import com.beancounter.common.utils.AssetUtils.Companion.split
import com.beancounter.common.utils.AssetUtils.Companion.toKey
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.lang.NonNull
import java.util.*

internal class TestAsset {
    private val objectMapper = ObjectMapper().registerModule(KotlinModule())

    @Test
    fun is_PriceRequestForAsset() {
        val priceRequest = of(getAsset("NASDAQ", "EBAY"))
        assertThat(priceRequest.assets).hasSize(1)
        assertThat(priceRequest.date).isEqualTo("today")
    }

    @Test
    @Throws(Exception::class)
    fun is_AssetResponse() {
        val assetResponse = AssetResponse(getAsset("as", "Blah"))
        assetResponse.data.marketCode = null // JsonIgnore
        val json = objectMapper.writeValueAsString(assetResponse)
        val fromJson = objectMapper.readValue(json, AssetResponse::class.java)
        assertThat(fromJson.data)
                .usingRecursiveComparison().isEqualTo(assetResponse.data)
    }

    @Test
    @Throws(Exception::class)
    fun assetRequestSerializes() {
        val asset = getJsonAsset("BBB", "AAA")
        val values = HashMap<String, AssetInput>()
        values[toKey(asset)] = AssetUtils.getAssetInput("BBB", "AAA")
        values["second"] = AssetUtils.getAssetInput("Whee", "Twee")
        val assetRequest = AssetRequest(values)
        assertThat(assetRequest.data)
                .containsKeys(toKey(asset))
        val json = objectMapper.writeValueAsString(assetRequest)
        val (data) = objectMapper.readValue(json, AssetRequest::class.java)
        assertThat(data).hasSize(assetRequest.data.size)
        for (key in data.keys) {
            assertThat(data[key])
                    .usingRecursiveComparison().isEqualTo(assetRequest.data[key])
        }
    }

    @Test
    @Throws(Exception::class)
    fun assetResponseSerializes() {
        val asset = getJsonAsset("BBB", "AAA")
        val assetMap = HashMap<String, Asset>()
        assetMap[toKey(asset)] = asset
        assetMap["second"] = getJsonAsset("Whee", "Twee")
        val assetUpdateResponse = AssetUpdateResponse(assetMap)
        assertThat(assetUpdateResponse.data).containsKeys(toKey(asset))
        val json = objectMapper.writeValueAsString(assetUpdateResponse)
        val fromJson = objectMapper.readValue(json, AssetUpdateResponse::class.java)
        assertThat(fromJson.data).containsKeys(toKey(asset))
    }

    @Test
    fun is_AssetKeyParsing() {
        val asset = getAsset(
                "MCODE",
                "ACODE")
        val keyIn = toKey(asset)
        assertThat(toKey(AssetInput("MCODE", "ACODE")))
                .isEqualTo(keyIn)
        val assetInput = getAssetInput(asset)
        assertThat(assetInput)
                .hasFieldOrProperty("code")
                .hasFieldOrProperty("market")
                .hasFieldOrProperty("resolvedAsset")
        assertThat(toKey(assetInput))
                .isEqualTo(keyIn)
        assertThat(fromKey(keyIn))
                .usingRecursiveComparison().isEqualTo(asset)
    }

    @Test
    fun is_AssetKeyExceptionsBeingThrown() {
        assertThrows(BusinessException::class.java) { fromKey("CodeWithNoMarket") }
        assertThrows(NullPointerException::class.java
        ) { getAsset((null as Market?)!!, "Twee") }
    }

    @Test
    fun is_AssetsSplitByMarket() {
        val assets: MutableCollection<AssetInput> = ArrayList()
        assets.add(getAssetInput("AAA", "ABC"))
        assets.add(getAssetInput("AAA", "123"))
        assets.add(getAssetInput("BBB", "ABC"))
        assets.add(getAssetInput("BBB", "123"))
        assets.add(getAssetInput("CCC", "123"))
        val results = split(assets)
        assertThat(results.size).isEqualTo(3)
        assertThat(results["AAA"]).hasSize(2)
        assertThat(results["BBB"]).hasSize(2)
        assertThat(results["CCC"]).hasSize(1)
    }

    @NonNull
    private fun getAssetInput(marketCode: String, assetCode: String): AssetInput {
        return getAssetInput(
                getAsset(marketCode, assetCode))
    }

    @Test
    @Throws(JsonProcessingException::class)
    fun is_SearchResponse() {
        val searchResult = AssetSearchResult(
                "Some Symbol",
                "Some Name",
                "Non Default",
                "Some Region",
                "USD"
        )
        val withDefaults = AssetSearchResult(
                "Symbol",
                "Name",
                null,
                "Some Region",
                "USD"
        )

        val results: MutableCollection<AssetSearchResult> = ArrayList()
        results.add(searchResult)
        results.add(withDefaults)
        val searchResponse = AssetSearchResponse(results)
        val json = objectMapper.writeValueAsString(searchResponse)
        val fromJson = objectMapper.readValue(json, AssetSearchResponse::class.java)
        assertThat(fromJson)
                .hasNoNullFieldsOrProperties()
                .usingRecursiveComparison().isEqualTo(searchResponse)
        assertThat(fromJson.data).isNotEmpty
        assertThat(fromJson.data.iterator().next().type).isNotNull
    }
    @Test
    fun is_Something () {
        val assetInput = AssetInput("ABC", "123")
        val ar = AssetRequest("ABC", assetInput)
        assertThat(ar.data).containsKey("ABC")
    }
}