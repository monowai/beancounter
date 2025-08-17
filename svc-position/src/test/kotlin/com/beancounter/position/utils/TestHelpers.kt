package com.beancounter.position.utils

import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.position.Constants
import com.beancounter.position.Constants.Companion.USD
import org.assertj.core.api.Assertions.assertThat
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Shared test utilities for svc-position tests to reduce code duplication and improve consistency.
 *
 * This object provides:
 * - Test data creation helpers
 * - Common assertion patterns
 * - Standardized test constants
 * - Position and portfolio creation utilities
 *
 * Usage:
 * ```
 * val portfolio = TestHelpers.createTestPortfolio("Test Portfolio")
 * val asset = TestHelpers.createTestAsset("AAPL", "NASDAQ")
 * val position = TestHelpers.createTestPosition(asset, portfolio)
 * val positions = TestHelpers.createTestPositions(portfolio, listOf(position))
 * ```
 */
object TestHelpers {
    // Standard test constants
    const val TEST_PORTFOLIO_ID = "test-portfolio"
    const val TEST_ASSET_CODE = "AAPL"
    const val TEST_MARKET_CODE = "NASDAQ"
    const val TEST_QUANTITY = "100"
    const val TEST_PRICE = "150.00"
    const val TEST_TRADE_DATE = "2024-01-15"

    /**
     * Creates a test portfolio with the specified ID and currency.
     */
    fun createTestPortfolio(
        id: String = TEST_PORTFOLIO_ID,
        currencyCode: String = USD.code,
        baseCurrency: String = USD.code
    ): Portfolio =
        Portfolio(
            id = id,
            currency = Currency(currencyCode),
            base = Currency(baseCurrency),
            owner = Constants.owner
        )

    /**
     * Creates a test asset with the specified code and market.
     */
    fun createTestAsset(
        code: String = TEST_ASSET_CODE,
        marketCode: String = TEST_MARKET_CODE
    ): Asset {
        val market = Market(marketCode)
        return Asset(
            code = code,
            id = code,
            name = "$code Corporation",
            market = market,
            priceSymbol = code
        )
    }

    /**
     * Creates a test position with the specified asset and portfolio.
     */
    fun createTestPosition(
        asset: Asset = createTestAsset(),
        portfolio: Portfolio = createTestPortfolio()
    ): Position =
        Position(
            asset = asset,
            portfolio = portfolio
        )

    /**
     * Creates test positions collection with the specified portfolio and positions.
     */
    fun createTestPositions(
        portfolio: Portfolio = createTestPortfolio(),
        positions: List<Position> = listOf(createTestPosition())
    ): Positions {
        val positionsCollection = Positions(portfolio)
        positions.forEach { positionsCollection.add(it) }
        return positionsCollection
    }

    /**
     * Creates a test market data with the specified asset and price.
     */
    fun createTestMarketData(
        asset: Asset = createTestAsset(),
        price: BigDecimal = BigDecimal(TEST_PRICE),
        priceDate: LocalDate = LocalDate.parse(TEST_TRADE_DATE)
    ): MarketData =
        MarketData(
            asset = asset,
            source = "TEST",
            priceDate = priceDate,
            close = price
        )

    /**
     * Creates a test asset input with the specified market and asset code.
     */
    fun createTestAssetInput(
        marketCode: String = TEST_MARKET_CODE,
        assetCode: String = TEST_ASSET_CODE
    ): AssetInput =
        AssetInput(
            market = marketCode,
            code = assetCode
        )

    /**
     * Creates a test transaction with the specified parameters.
     */
    fun createTestTransaction(
        asset: Asset = createTestAsset(),
        portfolio: Portfolio = createTestPortfolio(),
        trnType: TrnType = TrnType.BUY,
        quantity: BigDecimal = BigDecimal(TEST_QUANTITY),
        price: BigDecimal = BigDecimal(TEST_PRICE),
        tradeDate: LocalDate = LocalDate.parse(TEST_TRADE_DATE)
    ): Trn =
        Trn(
            asset = asset,
            portfolio = portfolio,
            trnType = trnType,
            quantity = quantity,
            price = price,
            tradeDate = tradeDate
        )

    /**
     * Asserts that a portfolio has the expected basic properties.
     */
    fun assertPortfolioProperties(
        portfolio: Portfolio,
        expectedId: String,
        expectedCurrencyCode: String
    ) {
        assertThat(portfolio.id).isEqualTo(expectedId)
        assertThat(portfolio.currency.code).isEqualTo(expectedCurrencyCode)
        assertThat(portfolio.owner).isNotNull()
    }

    /**
     * Asserts that an asset has the expected basic properties.
     */
    fun assertAssetProperties(
        asset: Asset,
        expectedCode: String,
        expectedMarketCode: String
    ) {
        assertThat(asset.code).isEqualTo(expectedCode)
        assertThat(asset.market.code).isEqualTo(expectedMarketCode)
        assertThat(asset.id).isNotNull()
    }

    /**
     * Asserts that a position has the expected basic properties.
     */
    fun assertPositionProperties(
        position: Position,
        expectedAssetCode: String,
        expectedPortfolioId: String
    ) {
        assertThat(position.asset.code).isEqualTo(expectedAssetCode)
        assertThat(position.asset.id).isNotNull()
    }

    /**
     * Asserts that positions collection has the expected properties.
     */
    fun assertPositionsProperties(
        positions: Positions,
        expectedPortfolioId: String,
        expectedSize: Int
    ) {
        assertThat(positions.portfolio.id).isEqualTo(expectedPortfolioId)
        assertThat(positions.positions).hasSize(expectedSize)
    }

    /**
     * Asserts that market data has the expected basic properties.
     */
    fun assertMarketDataProperties(
        marketData: MarketData,
        expectedAssetCode: String,
        expectedPrice: BigDecimal
    ) {
        assertThat(marketData.asset.code).isEqualTo(expectedAssetCode)
        assertThat(marketData.close).isEqualTo(expectedPrice)
        assertThat(marketData.id).isNotNull()
    }
}