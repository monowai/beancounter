package com.beancounter.event.utils

import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.TrnType
import com.beancounter.event.Constants
import com.beancounter.event.Constants.Companion.USD
import org.assertj.core.api.Assertions.assertThat
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Shared test utilities for svc-event tests to reduce code duplication and improve consistency.
 *
 * This object provides:
 * - Test data creation helpers
 * - Common assertion patterns
 * - Standardized test constants
 * - Event and corporate event creation utilities
 *
 * Usage:
 * ```
 * val event = TestHelpers.createTestEvent("Test Event")
 * val corporateEvent = TestHelpers.createTestCorporateEvent("AAPL", TrnType.DIVI)
 * val portfolio = TestHelpers.createTestPortfolio("Test Portfolio")
 * ```
 */
object TestHelpers {

    // Standard test constants
    const val TEST_EVENT_ID = "test-event-123"
    const val TEST_ASSET_CODE = "AAPL"
    const val TEST_MARKET_CODE = "NASDAQ"
    const val TEST_PORTFOLIO_ID = "test-portfolio"
    const val TEST_EVENT_TYPE = "DIVIDEND"
    const val TEST_RATE = "0.50"
    const val TEST_PRICE = "150.00"
    const val TEST_DATE = "2024-01-15"

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
            owner = com.beancounter.common.model.SystemUser("test-user", "test@example.com")
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
    ): com.beancounter.common.model.Positions {
        val positionsCollection = com.beancounter.common.model.Positions(portfolio)
        positions.forEach { positionsCollection.add(it) }
        return positionsCollection
    }

    /**
     * Creates a test market data with the specified asset and price.
     */
    fun createTestMarketData(
        asset: Asset = createTestAsset(),
        price: BigDecimal = BigDecimal(TEST_PRICE),
        priceDate: LocalDate = LocalDate.parse(TEST_DATE)
    ): MarketData =
        MarketData(
            asset = asset,
            source = "TEST",
            priceDate = priceDate,
            close = price
        )

    /**
     * Creates a test corporate event with the specified parameters.
     */
    fun createTestCorporateEvent(
        assetCode: String = TEST_ASSET_CODE,
        trnType: TrnType = TrnType.DIVI,
        rate: BigDecimal = BigDecimal(TEST_RATE),
        recordDate: LocalDate = LocalDate.parse(TEST_DATE)
    ): CorporateEvent =
        CorporateEvent(
            assetId = assetCode,
            trnType = trnType,
            rate = rate,
            recordDate = recordDate,
            source = "TEST"
        )

    /**
     * Creates a test trusted transaction event with the specified parameters.
     */
    fun createTestTrustedTrnEvent(
        portfolio: Portfolio = createTestPortfolio(),
        assetCode: String = TEST_ASSET_CODE,
        trnType: TrnType = TrnType.DIVI
    ): TrustedTrnEvent =
        TrustedTrnEvent(
            portfolio = portfolio,
            message = "Test event message",
            trnInput = com.beancounter.common.input.TrnInput(
                trnType = trnType,
                assetId = assetCode
            )
        )

    /**
     * Creates a test trusted transaction query with the specified parameters.
     */
    fun createTestTrustedTrnQuery(
        portfolio: Portfolio = createTestPortfolio(),
        assetId: String = TEST_ASSET_CODE,
        tradeDate: LocalDate = LocalDate.parse(TEST_DATE)
    ): TrustedTrnQuery =
        TrustedTrnQuery(
            portfolio = portfolio,
            tradeDate = tradeDate,
            assetId = assetId
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
     * Asserts that a corporate event has the expected basic properties.
     */
    fun assertCorporateEventProperties(
        event: CorporateEvent,
        expectedAssetId: String,
        expectedTrnType: TrnType,
        expectedRate: BigDecimal
    ) {
        assertThat(event.assetId).isEqualTo(expectedAssetId)
        assertThat(event.trnType).isEqualTo(expectedTrnType)
        assertThat(event.rate).isEqualTo(expectedRate)
        assertThat(event.source).isNotNull()
    }

    /**
     * Asserts that a trusted transaction event has the expected basic properties.
     */
    fun assertTrustedTrnEventProperties(
        event: TrustedTrnEvent,
        expectedPortfolioId: String,
        expectedAssetId: String
    ) {
        assertThat(event.portfolio.id).isEqualTo(expectedPortfolioId)
        assertThat(event.trnInput.assetId).isEqualTo(expectedAssetId)
        assertThat(event.message).isNotNull()
    }

    /**
     * Asserts that a trusted transaction query has the expected basic properties.
     */
    fun assertTrustedTrnQueryProperties(
        query: TrustedTrnQuery,
        expectedPortfolioId: String,
        expectedAssetId: String
    ) {
        assertThat(query.portfolio.id).isEqualTo(expectedPortfolioId)
        assertThat(query.assetId).isEqualTo(expectedAssetId)
        assertThat(query.tradeDate).isNotNull()
    }
}
