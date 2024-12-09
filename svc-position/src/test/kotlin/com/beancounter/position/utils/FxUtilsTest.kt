package com.beancounter.position.utils

import com.beancounter.common.model.Asset
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.IsoCurrencyPair.Companion.toPair
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.position.Constants
import com.beancounter.position.Constants.Companion.GBP
import com.beancounter.position.Constants.Companion.NASDAQ
import com.beancounter.position.Constants.Companion.NZD
import com.beancounter.position.Constants.Companion.SGD
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.accumulation.AccumulationStrategy
import com.beancounter.position.accumulation.BuyBehaviour
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * FX Currency Pair tests.
 */
internal class FxUtilsTest {
    private val fxUtils = FxUtils()

    @Test
    fun is_CurrencyPairResultsAsExpected() {
        val asset =
            Position(
                getTestAsset(
                    NASDAQ,
                    "Test"
                )
            ).asset
        val validCurrency = asset.market.currency
        assertThat(
            toPair(
                NZD,
                validCurrency
            )
        ).isNotNull // From != To
    }

    @Test
    fun is_FxRequestCorrect() {
        val portfolio =
            Portfolio(
                "ABC",
                currency = SGD
            )
        val positions = Positions(portfolio)
        val gbpMarket =
            Market(
                GBP.code,
                GBP.code
            )
        val usdMarket = Market(USD.code)

        positions.add(
            getPosition(
                getTestAsset(
                    gbpMarket,
                    "$GBP.code Asset"
                ),
                positions
            )
        )
        positions.add(
            getPosition(
                getTestAsset(
                    usdMarket,
                    "$USD.code Asset"
                ),
                positions
            )
        )
        positions.add(
            getPosition(
                getTestAsset(
                    usdMarket,
                    "$USD.code Asset Other"
                ),
                positions
            )
        )
        with(
            fxUtils.buildRequest(
                USD,
                positions
            )
        ) {
            assertThat(pairs).hasSize(3)
            assertThat(pairs).containsExactlyInAnyOrder(
                // TRADE:PF
                IsoCurrencyPair(
                    USD.code,
                    SGD.code
                ),
                // PF:TRADE
                IsoCurrencyPair(
                    GBP.code,
                    SGD.code
                ),
                // BASE:TRADE
                IsoCurrencyPair(
                    GBP.code,
                    USD.code
                )
            )
        }
    }

    private fun getPosition(
        asset: Asset,
        positions: Positions
    ): Position {
        val buyBehaviour: AccumulationStrategy = BuyBehaviour()
        return buyBehaviour.accumulate(
            Trn(
                trnType = TrnType.BUY,
                asset = asset,
                quantity = Constants.hundred,
                tradeAmount = BigDecimal("2000.00")
            ),
            positions
        )
    }
}