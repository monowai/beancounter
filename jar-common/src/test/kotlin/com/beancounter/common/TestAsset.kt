package com.beancounter.common

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.Status
import com.beancounter.common.utils.AssetKeyUtils.Companion.toKey
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.AssetUtils.Companion.getAssetInput
import com.beancounter.common.utils.AssetUtils.Companion.split
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Unit Tests for Asset Object
 */
internal class TestAsset {
    @Test
    fun assetDefaults() {
        val asset = getAsset(
            Market("Any"),
            "Thing"
        )
        assertThat(asset)
            .hasFieldOrPropertyWithValue("status", Status.Active)
            .hasFieldOrPropertyWithValue("version", "1")
    }

    @Test
    fun assetKeyParses() {
        val asset = getAsset(
            Market("MCODE"),
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
            .isEqualTo(asset)
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
        val assets: MutableCollection<PriceAsset> = ArrayList()
        val marketCodeA = "AAA"
        val assetCodeB = "ABC"
        assets.add(PriceAsset(marketCodeA, assetCodeB))
        val assetCodeOne = "123"
        assets.add(PriceAsset(marketCodeA, assetCodeOne))
        val marketCodeB = "BBB"
        assets.add(PriceAsset(marketCodeB, assetCodeB))
        assets.add(PriceAsset(marketCodeB, assetCodeOne))
        val marketCodeC = "CCC"
        assets.add(PriceAsset(marketCodeC, assetCodeOne))
        val results = split(assets)
        assertThat(results.size).isEqualTo(3)
        assertThat(results[marketCodeA]).hasSize(2)
        assertThat(results[marketCodeB]).hasSize(2)
        assertThat(results[marketCodeC]).hasSize(1)
    }

    @Test
    fun assetFoundInRequest() {
        val market = Market("ABC")
        val assetInput = AssetInput(market.code, "123")
        val ar = AssetRequest(market.code, assetInput)
        assertThat(ar.data).containsKey(market.code)
    }

    @Test
    fun defaultsFromAsset() {
        val assetInput = AssetInput(getAsset(Market("a"), "b"))
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
        return getAsset(Market(marketAsset[1]), marketAsset[0])
    }
}
