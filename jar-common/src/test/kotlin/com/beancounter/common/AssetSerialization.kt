package com.beancounter.common

import com.beancounter.common.TestMarkets.Companion.USD
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetResponse
import com.beancounter.common.contracts.AssetSearchResponse
import com.beancounter.common.contracts.AssetSearchResult
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.utils.AssetKeyUtils.Companion.toKey
import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.AssetUtils.Companion.getJsonAsset
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.fasterxml.jackson.core.JsonProcessingException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Payload serialization tests involving asset contracts
 */
class AssetSerialization {
    @Test
    @Throws(Exception::class)
    fun assetUpdateResponseSerializes() {
        val asset =
            getJsonAsset(
                "BBB",
                "AAA",
            )
        val assetMap = HashMap<String, Asset>()
        assetMap[toKey(asset)] = asset
        assetMap["second"] =
            getJsonAsset(
                "Whee",
                "Twee",
            )
        val assetUpdateResponse = AssetUpdateResponse(assetMap)
        assertThat(assetUpdateResponse.data).containsKeys(toKey(asset))
        val json = objectMapper.writeValueAsString(assetUpdateResponse)
        val fromJson =
            objectMapper.readValue(
                json,
                AssetUpdateResponse::class.java,
            )
        assertThat(fromJson.data).containsKeys(toKey(asset))
    }

    @Test
    @Throws(Exception::class)
    fun assetResponseSerializes() {
        val assetResponse =
            AssetResponse(
                Asset(
                    code = "YYY",
                    market = Market("XXX"),
                ),
            )
        val fromJson =
            objectMapper.readValue(
                objectMapper.writeValueAsString(assetResponse),
                AssetResponse::class.java,
            )
        assertThat(fromJson.data)
            .isEqualTo(assetResponse.data)
    }

    @Test
    @Throws(JsonProcessingException::class)
    fun searchResponse() {
        val searchResult =
            AssetSearchResult(
                "Some Symbol",
                "Some Name",
                "Non Default",
                "Some Region",
                USD.code,
            )
        val withDefaults =
            AssetSearchResult(
                "Symbol",
                "Name",
                "Equity",
                "Some Region",
                USD.code,
            )

        val results: MutableCollection<AssetSearchResult> = ArrayList()
        results.add(searchResult)
        results.add(withDefaults)
        val searchResponse = AssetSearchResponse(results)
        val json = objectMapper.writeValueAsString(searchResponse)
        val fromJson =
            objectMapper.readValue(
                json,
                AssetSearchResponse::class.java,
            )
        assertThat(fromJson)
            .hasNoNullFieldsOrProperties()
            .usingRecursiveComparison()
            .isEqualTo(searchResponse)
        assertThat(fromJson.data).isNotEmpty
        assertThat(
            fromJson.data
                .iterator()
                .next()
                .type,
        ).isNotNull
    }

    @Test
    @Throws(Exception::class)
    fun assetRequestSerializes() {
        val asset =
            getJsonAsset(
                "BBB",
                "AAA",
            )
        val values = HashMap<String, AssetInput>()
        values[toKey(asset)] =
            AssetUtils.getAssetInput(
                "BBB",
                "AAA",
            )
        values["second"] =
            AssetUtils.getAssetInput(
                "Whee",
                "Twee",
            )
        val assetRequest = AssetRequest(values)
        assertThat(assetRequest.data)
            .containsKeys(toKey(asset))
        val json = objectMapper.writeValueAsString(assetRequest)
        val (data) =
            objectMapper.readValue(
                json,
                AssetRequest::class.java,
            )
        assertThat(data).hasSize(assetRequest.data.size)
        for (key in data.keys) {
            assertThat(data[key])
                .usingRecursiveComparison()
                .isEqualTo(assetRequest.data[key])
        }
    }

    @Test
    fun dataProviderAsset() {
        val asset =
            AssetUtils.getTestAsset(
                Market("1"),
                "2",
            )
        val assetInput =
            AssetInput(
                market = "amarket",
                code = "acode",
                resolvedAsset = asset,
            )
        assertThat(assetInput)
            .hasFieldOrPropertyWithValue(
                "market",
                "amarket",
            ).hasFieldOrPropertyWithValue(
                "code",
                "acode",
            ).hasFieldOrPropertyWithValue(
                "resolvedAsset",
                asset,
            ).hasFieldOrPropertyWithValue(
                "name",
                null,
            )

        val json = objectMapper.writeValueAsString(assetInput)
        assertThat(
            objectMapper.readValue(
                json,
                AssetInput::class.java,
            ),
        ).hasNoNullFieldsOrPropertiesExcept(
            "resolvedAsset",
            "name",
            "currency",
            "owner",
        )
    }
}
