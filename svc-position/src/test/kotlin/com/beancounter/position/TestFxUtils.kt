package com.beancounter.position

import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.IsoCurrencyPair.Companion.toPair
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.position.Constants.Companion.GBP
import com.beancounter.position.Constants.Companion.NZD
import com.beancounter.position.Constants.Companion.SGD
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.utils.FxUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * FX Currency Pair tests.
 */
internal class TestFxUtils {
    private val fxUtils = FxUtils()

    @Test
    fun is_CurrencyPairResultsAsExpected() {
        val (asset) = Position(getAsset(USD.code, "Test"))
        val validCurrency = asset.market.currency
        assertThat(toPair(NZD, validCurrency))
            .isNotNull // From != To
    }

    @Test
    fun is_FxRequestCorrect() {
        val gbpMarket = Market(GBP.code, GBP)
        val gbpPosition = Position(getAsset(gbpMarket, "$GBP.code Asset"))
        val usdMarket = Market(USD.code, USD)
        val usdPosition = Position(getAsset(usdMarket, "$USD.code Asset"))
        val otherUsdPosition = Position(
            getAsset(usdMarket, "$USD.code Asset Other")
        )
        val portfolio = Portfolio("ABC", SGD)
        val positions = Positions(portfolio)
        positions.add(gbpPosition)
        positions.add(usdPosition)
        positions.add(otherUsdPosition)
        val (_, pairs) = fxUtils.buildRequest(USD, positions)
        assertThat(pairs).hasSize(3)
            .containsOnly(
                IsoCurrencyPair(SGD.code, USD.code), // PF:TRADE
                IsoCurrencyPair(SGD.code, GBP.code), // PF:TRADE
                IsoCurrencyPair(USD.code, GBP.code)
            ) // BASE:TRADE
    }
}
