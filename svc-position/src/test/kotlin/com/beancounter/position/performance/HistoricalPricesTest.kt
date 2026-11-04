package com.beancounter.position.performance

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.Constants.Companion.owner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * How one performance pass prices a position on a valuation date: the close on the
 * date, else a PRIVATE asset's latest price, else average cost.
 */
class HistoricalPricesTest {
    private val valuationDate = LocalDate.of(2024, 6, 3)
    private val portfolio =
        Portfolio(id = "hp-pf", code = "HP", name = "Historical prices", currency = USD, base = USD, owner = owner)
    private val listed = Asset(code = "LISTED", id = "LISTED-id", name = "Listed", market = Market("US", USD.code))

    @Test
    fun `should fall back to average cost when the close on the date is zero`() {
        val prices =
            HistoricalPrices(
                mapOf(valuationDate.toString() to listOf(MarketData(listed, valuationDate, close = BigDecimal.ZERO))),
                emptyMap(),
                TwrBoundary()
            )

        val unitValue = prices.on(valuationDate).unitValue(position(listed, averageCost = "20"), USD)

        assertThat(unitValue).isEqualByComparingTo("20")
    }

    private fun position(
        asset: Asset,
        averageCost: String
    ): Position =
        Position(asset, portfolio).apply {
            quantityValues.purchased = BigDecimal.TEN
            moneyValues.getValue(Position.In.TRADE).averageCost = BigDecimal(averageCost)
        }
}