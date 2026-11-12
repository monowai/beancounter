package com.beancounter.position.performance

import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Asset
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.position.Constants.Companion.SGD
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.Constants.Companion.owner
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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

    @Test
    fun `should keep a PRIVATE asset's last positive price when a later row closes at zero`() {
        val property = Asset(code = "HOUSE", id = "HOUSE-id", name = "House", market = Market("PRIVATE", USD.code))
        val priced = valuationDate.minusMonths(2)
        val zeroed = valuationDate.minusMonths(1)
        val prices =
            HistoricalPrices(
                mapOf(
                    priced.toString() to listOf(MarketData(property, priced, close = BigDecimal("500"))),
                    zeroed.toString() to listOf(MarketData(property, zeroed, close = BigDecimal.ZERO))
                ),
                emptyMap(),
                TwrBoundary()
            )

        val unitValue = prices.on(valuationDate).unitValue(position(property, averageCost = "20"), USD)

        assertThat(unitValue).isEqualByComparingTo("500")
    }

    @Test
    fun `should fail naming the pair and date when an FX rate is missing`() {
        val sgdListed =
            Asset(
                code = "SGLISTED",
                id = "SGLISTED-id",
                name = "SG listed",
                market = Market("SGX", currencyId = SGD.code)
            )
        val prices =
            HistoricalPrices(
                mapOf(valuationDate.toString() to listOf(MarketData(sgdListed, valuationDate, close = BigDecimal.TEN))),
                emptyMap(),
                TwrBoundary()
            )

        assertThatThrownBy { prices.on(valuationDate).unitValue(position(sgdListed, averageCost = "20"), USD) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("SGD:USD")
            .hasMessageContaining(valuationDate.toString())
    }

    @Test
    fun `should convert at the nearest prior rate when none is held on the date`() {
        val prices = sgdPrices(valuationDate.minusDays(3) to "0.70", valuationDate.plusDays(1) to "0.80")

        assertThat(prices.on(valuationDate).unitValue(position(sgdListed, averageCost = "20"), USD))
            .isEqualByComparingTo("14")
    }

    @Test
    fun `should convert at the rate held on the date ahead of a prior one`() {
        val prices = sgdPrices(valuationDate.minusDays(1) to "0.70", valuationDate to "0.75")

        assertThat(prices.on(valuationDate).unitValue(position(sgdListed, averageCost = "20"), USD))
            .isEqualByComparingTo("15")
    }

    @Test
    fun `should convert at the nearest later rate when none is held on or before the date`() {
        val prices = sgdPrices(valuationDate.plusYears(5) to "0.80", valuationDate.plusYears(6) to "0.90")

        assertThat(prices.on(valuationDate).unitValue(position(sgdListed, averageCost = "20"), USD))
            .isEqualByComparingTo("16")
    }

    @Test
    fun `should skip a date that holds no rate for the pair`() {
        val prices =
            HistoricalPrices(
                emptyMap(),
                mapOf(
                    valuationDate.minusDays(2).toString() to sgdToUsd("0.70"),
                    valuationDate.minusDays(1).toString() to FxPairResults(emptyMap())
                ),
                TwrBoundary()
            )

        assertThat(prices.on(valuationDate).unitValue(position(sgdListed, averageCost = "20"), USD))
            .isEqualByComparingTo("14")
    }

    private val sgdListed =
        Asset(code = "SGLISTED", id = "SGLISTED-id", name = "SG listed", market = Market("SGX", currencyId = SGD.code))

    private fun sgdToUsd(rate: String) =
        FxPairResults(
            mapOf(IsoCurrencyPair(SGD.code, USD.code) to FxRate(from = SGD, to = USD, rate = BigDecimal(rate)))
        )

    private fun sgdPrices(vararg rates: Pair<LocalDate, String>) =
        HistoricalPrices(
            emptyMap(),
            rates.associate { (date, rate) -> date.toString() to sgdToUsd(rate) },
            TwrBoundary()
        )

    private fun position(
        asset: Asset,
        averageCost: String
    ): Position =
        Position(asset, portfolio).apply {
            quantityValues.purchased = BigDecimal.TEN
            moneyValues.getValue(Position.In.TRADE).averageCost = BigDecimal(averageCost)
        }
}