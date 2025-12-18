package com.beancounter.common

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Unit tests for Price Contracts.
 */
class PriceRequestTest {
    @Test
    fun is_PriceRequestForAsset() {
        val priceRequest =
            PriceRequest.of(
                AssetUtils.getTestAsset(
                    Market("NASDAQ"),
                    "EBAY"
                )
            )
        assertThat(priceRequest.assets).hasSize(1)
        assertThat(priceRequest.date).isEqualTo(DateUtils.TODAY)
    }

    @Test
    @Disabled
    fun is_OfAssetInput() {
        val priceRequest =
            PriceRequest.of(
                AssetInput(
                    market = "ABC",
                    code = "123"
                )
            )
        assertThat(priceRequest.assets).hasSize(1)
        val dateUtils = DateUtils()
        assertThat(priceRequest.date).isEqualTo(dateUtils.offsetDateString(dateUtils.today()))
    }

    @Test
    fun `should exclude zero quantity positions from price request by default`() {
        val market = Market("NASDAQ")
        val portfolio = Portfolio("TEST")

        // Create positions - one with quantity, one without
        val assetWithQty = AssetUtils.getTestAsset(market, "AAPL")
        val assetZeroQty = AssetUtils.getTestAsset(market, "SOLD")

        val positionWithQty = Position(assetWithQty, portfolio)
        positionWithQty.quantityValues.purchased = BigDecimal("100")

        val positionZeroQty = Position(assetZeroQty, portfolio)
        // Zero quantity - sold out position (purchased = 0, sold = 0, total = 0)

        val positions = Positions(portfolio)
        positions.add(positionWithQty)
        positions.add(positionZeroQty)

        // Verify we have 2 positions total
        assertThat(positions.positions).hasSize(2)

        // Create price request with default (includeZeroQuantity = false)
        val priceRequest = PriceRequest.of(DateUtils.TODAY, positions)

        assertThat(priceRequest.assets)
            .hasSize(1)
            .extracting("code")
            .containsExactly("AAPL")
    }

    @Test
    fun `should include zero quantity positions when configured`() {
        val market = Market("NASDAQ")
        val portfolio = Portfolio("TEST")

        val assetWithQty = AssetUtils.getTestAsset(market, "AAPL")
        val assetZeroQty = AssetUtils.getTestAsset(market, "SOLD")

        val positionWithQty = Position(assetWithQty, portfolio)
        positionWithQty.quantityValues.purchased = BigDecimal("100")

        val positionZeroQty = Position(assetZeroQty, portfolio)

        val positions = Positions(portfolio)
        positions.add(positionWithQty)
        positions.add(positionZeroQty)

        // Create price request with includeZeroQuantity = true
        val priceRequest = PriceRequest.of(DateUtils.TODAY, positions, includeZeroQuantity = true)

        assertThat(priceRequest.assets)
            .hasSize(2)
            .extracting("code")
            .containsExactlyInAnyOrder("AAPL", "SOLD")
    }
}