package com.beancounter.marketdata.utils

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
import org.assertj.core.api.Assertions.assertThat
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Shared test utilities for svc-data tests to reduce code duplication and improve consistency.
 *
 * This object provides:
 * - Test data creation helpers
 * - Common assertion patterns
 * - MockMvc request helpers
 * - Standardized test constants
 *
 * Usage:
 * ```
 * val asset = TestHelpers.createTestAsset("AAPL", "NASDAQ")
 * val portfolio = TestHelpers.createTestPortfolio("Test Portfolio", "USD")
 * val trnInput = TestHelpers.createTestTrnInput(asset.id, TrnType.BUY)
 * ```
 */
object TestHelpers {
    
    // Standard test constants
    const val TEST_TRADE_DATE = "2024-01-15"
    const val TEST_QUANTITY = "10"
    const val TEST_PRICE = "150.00"
    const val TEST_CALLER_ID = "test-caller"
    
    /**
     * Creates a test asset with the specified code and market.
     */
    fun createTestAsset(code: String, marketCode: String): Asset {
        val market = Market(marketCode)
        return Asset(
            code = code,
            id = code,
            name = "$code Corporation",
            market = market,
            priceSymbol = code,
            category = "STOCK",
            assetCategory = AssetCategory("STOCK", "Stock")
        )
    }
    
    /**
     * Creates a test portfolio with the specified name and currency.
     */
    fun createTestPortfolio(name: String, currencyCode: String): Portfolio {
        val currency = Currency(currencyCode)
        return Portfolio(
            id = name.lowercase().replace(" ", "-"),
            name = name,
            currency = currency
        )
    }
    
    /**
     * Creates a test transaction input with the specified asset ID and transaction type.
     */
    fun createTestTrnInput(
        assetId: String,
        trnType: TrnType,
        quantity: BigDecimal = BigDecimal(TEST_QUANTITY),
        price: BigDecimal = BigDecimal(TEST_PRICE),
        tradeDate: LocalDate = LocalDate.parse(TEST_TRADE_DATE),
        tradeCurrency: String = USD.code,
        callerId: String = TEST_CALLER_ID
    ): TrnInput {
        return TrnInput(
            callerRef = CallerRef(callerId = callerId),
            assetId = assetId,
            trnType = trnType,
            quantity = quantity,
            price = price,
            tradeDate = tradeDate,
            tradeCurrency = tradeCurrency,
            tradeBaseRate = BigDecimal.ONE,
            tradeCashRate = BigDecimal.ONE,
            tradePortfolioRate = BigDecimal.ONE
        )
    }
    
    /**
     * Creates a test transaction request with the specified portfolio and transaction inputs.
     */
    fun createTestTrnRequest(portfolioId: String, trnInputs: Array<TrnInput>): TrnRequest {
        return TrnRequest(portfolioId, trnInputs)
    }
    
    /**
     * Creates a test asset request with the specified asset input and name.
     */
    fun createTestAssetRequest(assetInput: AssetInput, name: String): AssetRequest {
        return AssetRequest(assetInput, name)
    }
    
    /**
     * Creates a test portfolio input with the specified name and currency.
     */
    fun createTestPortfolioInput(name: String, currencyCode: String): PortfolioInput {
        return PortfolioInput(name, "Test Portfolio", currency = currencyCode)
    }
    
    /**
     * Performs a GET request to retrieve a transaction by ID.
     */
    fun getTransactionById(mockMvc: MockMvc, trnId: String, token: Jwt): MvcResult {
        return mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/trns/{trnId}", trnId)
                    .contentType("application/json")
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
            )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType("application/json"))
            .andReturn()
    }
    
    /**
     * Performs a DELETE request to delete a transaction by ID.
     */
    fun deleteTransactionById(mockMvc: MockMvc, trnId: String, token: Jwt): MvcResult {
        return mockMvc
            .perform(
                MockMvcRequestBuilders
                    .delete("/trns/{trnId}", trnId)
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
            )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType("application/json"))
            .andReturn()
    }
    
    /**
     * Performs a GET request to retrieve transactions by portfolio and asset.
     */
    fun getTransactionsByPortfolioAndAsset(
        mockMvc: MockMvc,
        portfolioId: String,
        assetId: String,
        token: Jwt
    ): MvcResult {
        return mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/trns/{portfolioId}/asset/{assetId}/trades", portfolioId, assetId)
                    .contentType("application/json")
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
            )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType("application/json"))
            .andReturn()
    }
    
    /**
     * Asserts that a transaction has the expected basic properties.
     */
    fun assertTransactionProperties(
        transaction: com.beancounter.common.model.Trn,
        expectedAssetId: String,
        expectedTrnType: TrnType,
        expectedQuantity: BigDecimal,
        expectedPrice: BigDecimal
    ) {
        assertThat(transaction.asset.id).isEqualTo(expectedAssetId)
        assertThat(transaction.trnType).isEqualTo(expectedTrnType)
        assertThat(transaction.quantity).isEqualTo(expectedQuantity)
        assertThat(transaction.price).isEqualTo(expectedPrice)
        assertThat(transaction.id).isNotNull()
        assertThat(transaction.callerRef).isNotNull()
        assertThat(transaction.callerRef!!.callerId).isNotNull()
    }
    
    /**
     * Asserts that a portfolio has the expected basic properties.
     */
    fun assertPortfolioProperties(
        portfolio: Portfolio,
        expectedName: String,
        expectedCurrencyCode: String
    ) {
        assertThat(portfolio.name).isEqualTo(expectedName)
        assertThat(portfolio.currency.code).isEqualTo(expectedCurrencyCode)
        assertThat(portfolio.id).isNotNull
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
        assertThat(asset.id).isNotNull
    }
}
