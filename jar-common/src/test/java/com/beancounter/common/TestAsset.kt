package com.beancounter.common

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.utils.AssetKeyUtils.Companion.toKey
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.AssetUtils.Companion.getAssetInput
import com.beancounter.common.utils.AssetUtils.Companion.split
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.lang.NonNull

/**
 * Unit Tests for Asset Object
 */
internal class TestAsset {

    @Test
    fun assetKeyParses() {
        val asset = getAsset(
            "MCODE",
            "ACODE"
        )
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
    fun assetKeyExceptionsBeingThrown() {
        assertThrows(BusinessException::class.java) { fromKey("CodeWithNoMarket") }
        assertThrows(
            NullPointerException::class.java
        ) { getAsset((null as Market?)!!, "Twee") }
    }

    @Test
    fun assetsSplitByMarket() {
        val assets: MutableCollection<AssetInput> = ArrayList()
        val marketCodeA = "AAA"
        val assetCodeB = "ABC"
        assets.add(getAssetInput(marketCodeA, assetCodeB))
        val assetCodeOne = "123"
        assets.add(getAssetInput(marketCodeA, assetCodeOne))
        val marketCodeB = "BBB"
        assets.add(getAssetInput(marketCodeB, assetCodeB))
        assets.add(getAssetInput(marketCodeB, assetCodeOne))
        val marketCodeC = "CCC"
        assets.add(getAssetInput(marketCodeC, assetCodeOne))
        val results = split(assets)
        assertThat(results.size).isEqualTo(3)
        assertThat(results[marketCodeA]).hasSize(2)
        assertThat(results[marketCodeB]).hasSize(2)
        assertThat(results[marketCodeC]).hasSize(1)
    }

    @NonNull
    private fun getAssetInput(marketCode: String, assetCode: String): AssetInput {
        return getAssetInput(
            getAsset(marketCode, assetCode)
        )
    }

    @Test
    fun assetFoundInRequest() {
        val assetInput = AssetInput("ABC", "123")
        val ar = AssetRequest("ABC", assetInput)
        assertThat(ar.data).containsKey("ABC")
    }

    @Test
    fun defaultsFromAsset() {
        val assetInput = AssetInput(getAsset("a", "b"))
        assertThat(assetInput)
            .hasFieldOrPropertyWithValue("market", "a")
            .hasFieldOrPropertyWithValue("code", "B")
            .hasFieldOrProperty("resolvedAsset")
    }

    private fun fromKey(key: String): Asset {
        val marketAsset = key.split(":").toTypedArray()
        if (marketAsset.size != 2) {
            throw BusinessException(String.format("Unable to parse the key %s", key))
        }
        return getAsset(marketAsset[1], marketAsset[0])
    }
}
