package com.beancounter.common

import com.beancounter.common.Constants.Companion.NYSE
import com.beancounter.common.Constants.Companion.SGD
import com.beancounter.common.TestMarkets.Companion.NZD
import com.beancounter.common.TestMarkets.Companion.USD
import com.beancounter.common.contracts.PositionRequest
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.DateValues
import com.beancounter.common.model.Market
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Totals
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.AssetUtils.Companion.getJsonAsset
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PortfolioUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

internal class TestPositions {
    private val dateUtils = DateUtils()
    private val bcJson = BcJson()

    @Test
    @Throws(Exception::class)
    fun is_PositionResponseChainSerializing() {
        val asset = getJsonAsset("MARKET", "TEST")
        val positions = Positions(PortfolioUtils.getPortfolio("T", SGD))
        val position = positions.add(Position(asset))

        assertThat(position.getMoneyValues(Position.In.TRADE, asset.market.currency).currency)
            .isEqualTo(USD)
        position.getMoneyValues(Position.In.TRADE, asset.market.currency).dividends = BigDecimal("100")
        position.quantityValues.purchased = BigDecimal(200)

        position.dateValues = DateValues()
            .apply { opened = dateUtils.date; closed = dateUtils.date; last = dateUtils.date; }

        val positionResponse = PositionResponse(positions)
        val json = bcJson.objectMapper.writeValueAsString(positionResponse)
        val fromJson = bcJson.objectMapper.readValue(json, PositionResponse::class.java)
        fromJson.data.positions.forEach { inPosition ->
            assertThat(inPosition.value.dateValues).isNotNull
            assertThat(inPosition.value.moneyValues).isNotNull
            assertThat(inPosition.value.asset).isNotNull
            assertThat(inPosition.value.asset.market).isNotNull
        }
    }

    @Test
    fun is_MixedTradeCurrenciesHandled() {
        val usdAsset = Asset("USDAsset", Market("USMarket"))
        val nzdAsset = Asset("NZDAsset", Market("NZMarket", NZD.code))
        val positions = Positions(PortfolioUtils.getPortfolio("MixedCurrencyTest"))
        assertThat(positions.isMixedCurrencies).isFalse
        positions.add(positions[usdAsset])
        assertThat(positions.isMixedCurrencies).isFalse
        positions.add(positions[nzdAsset])
        assertThat(positions.isMixedCurrencies).isTrue
    }

    @Test
    fun is_DateValuesSetFromTransaction() {
        val asset = getAsset(Market("Code"), "Dates")
        val expectedDate = "2018-12-01"
        val firstTradeDate = dateUtils.getDate(expectedDate)
        val secondTradeDate = dateUtils.getDate("2018-12-02")
        val positions = Positions(PortfolioUtils.getPortfolio("Twee"))
        var position = positions[asset, firstTradeDate]
        positions.add(position)
        // Calling this should not set the "first" trade date.
        position = positions[asset, secondTradeDate]
        assertThat(position.dateValues)
            .hasFieldOrPropertyWithValue("opened", dateUtils.getDate(expectedDate))
    }

    @Test
    fun is_GetPositionNonNull() {
        val positions = Positions(PortfolioUtils.getPortfolio())
        val asset = getAsset(NYSE, "AnyCode")
        val position = positions[asset]
        assertThat(position).isNotNull.hasFieldOrPropertyWithValue("asset", asset)
    }

    @Test
    @Throws(Exception::class)
    fun is_PositionRequestSerializing() {
        val trns: MutableCollection<Trn> = ArrayList()
        val trn = Trn(
            id = "any",
            trnType = TrnType.BUY,
            asset = getJsonAsset("RandomMarket", "Blah"),
            portfolio = PortfolioUtils.getPortfolio(),
        )
        trns.add(trn)
        val positionRequest = PositionRequest("TWEE", trns)

        val json = bcJson.objectMapper.writeValueAsString(positionRequest)
        val fromJson = bcJson.objectMapper.readValue(json, PositionRequest::class.java)
        assertThat(fromJson.portfolioId).isEqualTo(positionRequest.portfolioId)
        assertThat(fromJson.trns).hasSize(positionRequest.trns.size)
        for (trnJson in fromJson.trns) {
            assertThat(trnJson.portfolio).isNotNull
        }
    }

    @Test
    @Throws(Exception::class)
    fun is_TotalsSerializing() {
        val totals = Totals(BigDecimal("200.99"))
        val json = bcJson.objectMapper.writeValueAsString(totals)
        val fromJson = bcJson.objectMapper.readValue(json, Totals::class.java)
        assertThat(fromJson)
            .usingRecursiveComparison().isEqualTo(totals)
    }
}
