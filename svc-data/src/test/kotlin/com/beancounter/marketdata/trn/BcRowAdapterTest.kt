package com.beancounter.marketdata.trn

import com.beancounter.client.ingest.AssetIngestService
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.Constants.Companion.CASH_MARKET
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.Constants.Companion.nzdCashBalance
import com.beancounter.marketdata.Constants.Companion.usdCashBalance
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.currency.CurrencyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension

/**
 * BC Row Adapter tests for handling various assertions around transformations.
 */
@ExtendWith(MockitoExtension::class)
class BcRowAdapterTest {
    @Mock
    private lateinit var assetFinder: AssetFinder

    @Mock
    private lateinit var currencyService: CurrencyService

    @Mock
    private lateinit var assetService: AssetService

    @Mock
    private lateinit var ais: AssetIngestService

    private lateinit var bcRowAdapter: BcRowAdapter
    private lateinit var cashTrnServices: CashTrnServices

    private val assetCode = "CDNA"
    private val csv = "CSV"
    private val portfolio: Portfolio = Portfolio(csv)
    private val asset = Asset(code = assetCode, market = NASDAQ)

    @BeforeEach
    fun setUp() {
        cashTrnServices = CashTrnServices(assetFinder, assetService, currencyService)
        bcRowAdapter = BcRowAdapter(ais, cashTrnServices)

        setupMocks()
    }

    private fun setupMocks() {
        lenient()
            .`when`(
                ais.resolveAsset(
                    AssetInput(
                        NASDAQ.code,
                        assetCode,
                        "Caredx",
                        owner = portfolio.owner.id
                    )
                )
            ).thenReturn(asset)

        lenient()
            .`when`(
                ais.resolveAsset(
                    AssetInput(
                        CASH_MARKET.code,
                        NZD.code,
                        name = "",
                        owner = portfolio.owner.id
                    )
                )
            ).thenReturn(nzdCashBalance)

        lenient()
            .`when`(
                ais.resolveAsset(
                    AssetInput(
                        CASH_MARKET.code,
                        USD.code,
                        "",
                        owner = portfolio.owner.id
                    )
                )
            ).thenReturn(usdCashBalance)

        lenient()
            .`when`(
                assetService.findOrCreate(
                    AssetInput(
                        CASH_MARKET.code,
                        NZD.code
                    )
                )
            ).thenReturn(nzdCashBalance)

        lenient()
            .`when`(
                assetService.findOrCreate(
                    AssetInput(
                        CASH_MARKET.code,
                        USD.code
                    )
                )
            ).thenReturn(usdCashBalance)

        lenient()
            .`when`(assetService.find(USD.code))
            .thenReturn(usdCashBalance)

        lenient()
            .`when`(assetService.find(NZD.code))
            .thenReturn(nzdCashBalance)
    }

    @Test
    fun `should handle trimmed CSV input values`() {
        val values =
            listOf(
                "USX",
                "Kt-1jW3x1g",
                "BUY",
                "NASDAQ",
                assetCode,
                "Caredx",
                "USD",
                "USD",
                "2021-08-11",
                "200.000000",
                "1.000000",
                "USD",
                "77.780000",
                "0.00",
                "1.386674",
                "2000.00",
                "-2000.00",
                ""
            )

        // BC will receive data in the same manner
        val trustedTrnImportRequest = trustedTrnImportRequest(values)

        val result = bcRowAdapter.transform(trustedTrnImportRequest)
        // Transformation should still resolve without extra spaces.
        assertThat(result).isNotNull()
        assertThat(result.assetId).isEqualTo(asset.id)
        assertThat(result.trnType).isEqualTo(TrnType.BUY)
    }

    @Test
    fun `should handle missing asset gracefully`() {
        val values =
            listOf(
                "USX",
                "Kt-1jW3x1g",
                "BUY",
                "NASDAQ",
                "INVALID",
                "Caredx",
                "USD",
                "USD",
                "2021-08-11",
                "200.000000",
                "1.000000",
                "USD",
                "77.780000",
                "0.00",
                "1.386674",
                "2000.00",
                "-2000.00",
                ""
            )

        // Mock the AssetIngestService to throw BusinessException for invalid asset
        lenient()
            .`when`(
                ais.resolveAsset(
                    AssetInput(
                        NASDAQ.code,
                        "INVALID",
                        "Caredx",
                        owner = portfolio.owner.id
                    )
                )
            ).thenThrow(BusinessException("Asset not found"))

        val trustedTrnImportRequest = trustedTrnImportRequest(values)

        assertThrows(BusinessException::class.java) {
            bcRowAdapter.transform(trustedTrnImportRequest)
        }
    }

    @Test
    fun `should validate trade date format`() {
        val values =
            listOf(
                "USX",
                "Kt-1jW3x1g",
                "BUY",
                "NASDAQ",
                assetCode,
                "Caredx",
                "USD",
                "USD",
                "invalid-date",
                "200.000000",
                "1.000000",
                "USD",
                "77.780000",
                "0.00",
                "1.386674",
                "2000.00",
                "-2000.00",
                ""
            )

        val trustedTrnImportRequest = trustedTrnImportRequest(values)

        assertThrows(BusinessException::class.java) {
            bcRowAdapter.transform(trustedTrnImportRequest)
        }
    }

    @Test
    fun `should handle different transaction types`() {
        val values =
            listOf(
                "USX",
                "Kt-1jW3x1g",
                "SELL",
                "NASDAQ",
                assetCode,
                "Caredx",
                "USD",
                "USD",
                "2021-08-11",
                "200.000000",
                "1.000000",
                "USD",
                "77.780000",
                "0.00",
                "1.386674",
                "2000.00",
                "-2000.00",
                ""
            )

        val trustedTrnImportRequest = trustedTrnImportRequest(values)

        val result = bcRowAdapter.transform(trustedTrnImportRequest)
        assertThat(result).isNotNull()
        assertThat(result.trnType).isEqualTo(TrnType.SELL)
    }

    @Test
    fun `INCOME on private account should settle to same account`() {
        val privateMarket = Market("PRIVATE")
        val privateCash =
            Asset(
                code = "e2e-test.SGD-SAVINGS",
                id = "sgd-savings-id",
                name = "SGD Savings Account",
                market = privateMarket,
                category = AssetCategory.ACCOUNT,
                assetCategory = AssetCategory(AssetCategory.ACCOUNT, "Account")
            )

        lenient()
            .`when`(
                ais.resolveAsset(
                    AssetInput(
                        "PRIVATE",
                        "SGD-SAVINGS",
                        name = "",
                        owner = portfolio.owner.id
                    )
                )
            ).thenReturn(privateCash)

        // CSV: INCOME on PRIVATE asset, empty CashAccount
        val values =
            listOf(
                "20250115", // Batch
                "", // CallerId
                "INCOME", // Type
                "PRIVATE", // Market
                "SGD-SAVINGS", // Code
                "", // Name
                "", // CashAccount (empty — should auto-resolve to asset)
                "SGD", // CashCurrency
                "2025-01-15", // Date
                "1", // Quantity
                "", // BaseRate
                "SGD", // TradeCurrency
                "150", // Price
                "0", // Fees
                "", // PortfolioRate
                "", // TradeAmount
                "150", // CashAmount
                "Interest" // Comments
            )

        val result = bcRowAdapter.transform(trustedTrnImportRequest(values))

        assertThat(result.trnType).isEqualTo(TrnType.INCOME)
        // Cash should settle to the private account itself, not a generic SGD Balance
        assertThat(result.cashAssetId).isEqualTo(privateCash.id)
    }

    private fun trustedTrnImportRequest(values: List<String>): TrustedTrnImportRequest =
        TrustedTrnImportRequest(
            portfolio = portfolio,
            row = values
        )
}