package com.beancounter.marketdata.trn.broker

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * [BrokerHoldingAggregation] nets settled trns into holdings per asset and per portfolio,
 * applying a split to the quantity held when it occurs.
 */
class BrokerHoldingAggregationTest {
    private val asset = Asset(code = "AGG", market = NASDAQ)
    private val other = Asset(code = "OTHER", market = NASDAQ)
    private val first = Portfolio("first")
    private val second = Portfolio("second")
    private val day = LocalDate.of(2024, 3, 1)

    private fun trn(
        type: TrnType,
        quantity: String,
        tradeDate: LocalDate = day,
        portfolio: Portfolio = first,
        on: Asset = asset
    ) = Trn(
        trnType = type,
        asset = on,
        quantity = BigDecimal(quantity),
        portfolio = portfolio,
        tradeDate = tradeDate
    )

    @Test
    fun `a same-day split applies after the buy and before the sell`() {
        // Listed sell-first to prove ordering comes from trade type, not input order.
        val holdings =
            BrokerHoldingAggregation.holdings(
                listOf(
                    trn(TrnType.SELL, "50"),
                    trn(TrnType.SPLIT, "4"),
                    trn(TrnType.BUY, "100")
                )
            )

        assertThat(holdings).hasSize(1)
        // 100 bought, split 4:1 -> 400, then 50 sold -> 350
        assertThat(holdings.single().quantity).isEqualByComparingTo("350")
    }

    @Test
    fun `assets that net to zero are dropped but sold-out portfolio groups are kept`() {
        val holdings =
            BrokerHoldingAggregation.holdings(
                listOf(
                    trn(TrnType.BUY, "10", portfolio = first),
                    trn(TrnType.BUY, "5", portfolio = second),
                    trn(TrnType.SELL, "5", tradeDate = day.plusDays(1), portfolio = second),
                    trn(TrnType.BUY, "3", on = other),
                    trn(TrnType.SELL, "3", tradeDate = day.plusDays(1), on = other)
                )
            )

        assertThat(holdings).extracting<String> { it.assetCode }.containsExactly("AGG")
        assertThat(holdings.single().portfolioGroups)
            .extracting<String> { it.portfolioId }
            .containsExactly("first", "second")
    }

    @Test
    fun `an asset holding leaves out sold-out portfolio groups`() {
        val holding =
            BrokerHoldingAggregation.assetHolding(
                asset.id,
                listOf(
                    trn(TrnType.BUY, "10", portfolio = first),
                    trn(TrnType.BUY, "5", portfolio = second),
                    trn(TrnType.SELL, "5", tradeDate = day.plusDays(1), portfolio = second)
                )
            )

        assertThat(holding).isNotNull
        assertThat(holding!!.portfolioGroups).extracting<String> { it.portfolioId }.containsExactly("first")
        assertThat(holding.quantity).isEqualByComparingTo("10")
    }

    @Test
    fun `an asset holding is null when every portfolio is sold out`() {
        val holding =
            BrokerHoldingAggregation.assetHolding(
                asset.id,
                listOf(trn(TrnType.BUY, "5"), trn(TrnType.SELL, "5", tradeDate = day.plusDays(1)))
            )

        assertThat(holding).isNull()
    }
}