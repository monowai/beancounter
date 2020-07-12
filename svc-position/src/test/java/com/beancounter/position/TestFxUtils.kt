package com.beancounter.position

import com.beancounter.common.model.*
import com.beancounter.common.model.IsoCurrencyPair.Companion.toPair
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.CurrencyUtils
import com.beancounter.position.utils.FxUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class TestFxUtils {
    private val fxUtils = FxUtils()
    private val currencyUtils = CurrencyUtils()

    @Test
    fun is_CurrencyPairResultsAsExpected() {
        val (asset) = Position(getAsset("USD", "Test"))
        val validCurrency = asset.market.currency
        assertThat(toPair(currencyUtils.getCurrency("NZD"), validCurrency))
                .isNotNull // From != To
    }

    @Test
    fun is_FxRequestCorrect() {
        val usd = currencyUtils.getCurrency("USD")
        val gbpMarket = Market("GBP", currencyUtils.getCurrency("GBP"))
        val gbpPosition = Position(getAsset(gbpMarket, "GBP Asset"))
        val usdMarket = Market("USD", currencyUtils.getCurrency("USD"))
        val usdPosition = Position(getAsset(usdMarket, "USD Asset"))
        val otherUsdPosition = Position(
                getAsset(usdMarket, "USD Asset Other"))
        val portfolio = Portfolio("ABC", currencyUtils.getCurrency("SGD"))
        val positions = Positions(portfolio)
        positions.add(gbpPosition)
        positions.add(usdPosition)
        positions.add(otherUsdPosition)
        val (_, pairs) = fxUtils.buildRequest(usd, positions)
        assertThat(pairs).hasSize(3)
                .containsOnly(
                        IsoCurrencyPair("SGD", "USD"),  // PF:TRADE
                        IsoCurrencyPair("SGD", "GBP"),  // PF:TRADE
                        IsoCurrencyPair("USD", "GBP")) // BASE:TRADE
    }
}