package com.beancounter.marketdata.trn

import com.beancounter.client.ingest.AssetIngestService
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.Constants.Companion.SGD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.currency.CurrencyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension

/**
 * Test FX transactions between private bank accounts.
 * The frontend sends asset codes with owner prefix (e.g., "ownerId.SCB-USD")
 * but the backend stores assets with this full code.
 */
@ExtendWith(MockitoExtension::class)
class FxBetweenPrivateAccountsTest {
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

    private val ownerId = "2xstJu-9QzaiX_Ou1U2Ssw"
    private val owner = SystemUser(id = ownerId)
    private val portfolio = Portfolio(id = "portfolio-1", code = "SGD", owner = owner, currency = SGD)

    private val privateMarket = Market(code = "PRIVATE", currency = USD)

    // Bank account assets with owner prefix in code (as stored in DB)
    private val scbUsdCode = "$ownerId.SCB-USD"
    private val scbSgdCode = "$ownerId.SCB-SGD"

    private val scbUsdAccount =
        Asset(
            id = "scb-usd-id",
            code = scbUsdCode,
            name = "SCB USD Account",
            market = privateMarket,
            marketCode = "PRIVATE",
            priceSymbol = USD.code,
            category = "ACCOUNT",
            assetCategory = AssetCategory("ACCOUNT", "Bank Account"),
            systemUser = owner
        )

    private val scbSgdAccount =
        Asset(
            id = "scb-sgd-id",
            code = scbSgdCode,
            name = "SCB SGD Account",
            market = privateMarket,
            marketCode = "PRIVATE",
            priceSymbol = SGD.code,
            category = "ACCOUNT",
            assetCategory = AssetCategory("ACCOUNT", "Bank Account"),
            systemUser = owner
        )

    @BeforeEach
    fun setUp() {
        cashTrnServices = CashTrnServices(assetFinder, assetService, currencyService)
        bcRowAdapter = BcRowAdapter(ais, cashTrnServices)
    }

    /**
     * Test FX_BUY transaction: Buy USD (into SCB-USD) / Sell SGD (from SCB-SGD)
     *
     * The frontend sends: FX_BUY,PRIVATE,ownerId.SCB-USD,...,SGD,...
     * This means: Buy into the SCB-USD account, sell SGD
     */
    @Test
    fun `should handle FX_BUY between private bank accounts with owner prefix in code`() {
        // Mock asset resolution - the code includes owner prefix
        lenient()
            .`when`(
                ais.resolveAsset(
                    AssetInput(
                        market = "PRIVATE",
                        code = scbUsdCode, // Full code with owner prefix
                        name = "",
                        owner = ownerId
                    )
                )
            ).thenReturn(scbUsdAccount)

        // CSV row format:
        // Batch,CallerId,Type,Market,Code,Name,CashAccount,CashCurrency,
        // Date,Quantity,BaseRate,TradeCurrency,Price,Fees,PortfolioRate,
        // TradeAmount,CashAmount,Comments,Status
        val csvRow =
            listOf(
                "20260121", // Batch
                "", // CallerId
                "FX_BUY", // Type - Buy USD
                "PRIVATE", // Market
                scbUsdCode, // Code - the USD account (with owner prefix)
                "", // Name
                "", // CashAccount (settlement account)
                "SGD", // CashCurrency - selling SGD
                "2026-01-21", // Date
                "4600", // Quantity - amount of USD bought
                "", // BaseRate
                scbUsdCode, // TradeCurrency - USD
                "1", // Price
                "0", // Fees
                "", // PortfolioRate
                "", // TradeAmount
                "-5958.77", // CashAmount - SGD sold (negative)
                "Buy USD/Sell SGD", // Comments
                "SETTLED" // Status
            )

        val request =
            TrustedTrnImportRequest(
                portfolio = portfolio,
                row = csvRow
            )

        val result = bcRowAdapter.transform(request)

        assertThat(result).isNotNull
        assertThat(result.trnType).isEqualTo(TrnType.FX_BUY)
        assertThat(result.assetId).isEqualTo(scbUsdAccount.id)
    }

    /**
     * Test FX_BUY transaction between two bank accounts where the sell asset CODE is provided.
     * This tests the scenario: Buy USD (into SCB-USD) / Sell SGD (from SCB-SGD)
     * The frontend sends the sell asset CODE (e.g., "SCB-SGD") in the CashAccount field.
     */
    @Test
    fun `should resolve sell asset by code from CashAccount field for FX_BUY`() {
        // Mock buy asset resolution
        lenient()
            .`when`(
                ais.resolveAsset(
                    AssetInput(
                        market = "PRIVATE",
                        code = scbUsdCode,
                        name = "",
                        owner = ownerId
                    )
                )
            ).thenReturn(scbUsdAccount)

        // Mock that find("SCB-SGD") throws (it's not a valid UUID)
        lenient()
            .`when`(assetFinder.find("SCB-SGD"))
            .thenThrow(
                com.beancounter.common.exception
                    .NotFoundException("Asset not found: SCB-SGD")
            )

        // Mock sell asset lookup by code (the new approach!)
        // The code sent is "SCB-SGD" (without owner prefix) but findLocally
        // will prepend the owner prefix internally
        lenient()
            .`when`(
                assetFinder.findLocally(
                    AssetInput(
                        market = "PRIVATE",
                        code = "SCB-SGD", // Code without owner prefix
                        owner = ownerId
                    )
                )
            ).thenReturn(scbSgdAccount)

        // CSV row with sell asset CODE in CashAccount field
        val csvRow =
            listOf(
                "20260121", // Batch
                "", // CallerId
                "FX_BUY", // Type - Buy USD
                "PRIVATE", // Market
                scbUsdCode, // Code - the USD account (buy side)
                "", // Name
                "SCB-SGD", // CashAccount - SELL ASSET CODE (human readable!)
                "SGD", // CashCurrency - selling SGD
                "2026-01-21", // Date
                "4600", // Quantity - amount of USD bought
                "", // BaseRate
                "USD", // TradeCurrency - USD
                "1", // Price
                "0", // Fees
                "", // PortfolioRate
                "", // TradeAmount
                "-5958.77", // CashAmount - SGD sold (negative)
                "Buy USD/Sell SGD", // Comments
                "SETTLED" // Status
            )

        val request =
            TrustedTrnImportRequest(
                portfolio = portfolio,
                row = csvRow
            )

        val result = bcRowAdapter.transform(request)

        assertThat(result).isNotNull
        assertThat(result.trnType).isEqualTo(TrnType.FX_BUY)
        assertThat(result.assetId).isEqualTo(scbUsdAccount.id)
        // The key assertion: cashAssetId should be the sell asset's ID
        assertThat(result.cashAssetId).isEqualTo(scbSgdAccount.id)
    }

    /**
     * Test FX_BUY still works with UUID in CashAccount (backward compatibility)
     */
    @Test
    fun `should still support UUID in CashAccount for backward compatibility`() {
        // Mock buy asset resolution
        lenient()
            .`when`(
                ais.resolveAsset(
                    AssetInput(
                        market = "PRIVATE",
                        code = scbUsdCode,
                        name = "",
                        owner = ownerId
                    )
                )
            ).thenReturn(scbUsdAccount)

        // Mock sell asset lookup by UUID
        lenient()
            .`when`(assetFinder.find(scbSgdAccount.id))
            .thenReturn(scbSgdAccount)

        // CSV row with sell asset UUID in CashAccount field
        val csvRow =
            listOf(
                "20260121", // Batch
                "", // CallerId
                "FX_BUY", // Type - Buy USD
                "PRIVATE", // Market
                scbUsdCode, // Code - the USD account (buy side)
                "", // Name
                scbSgdAccount.id, // CashAccount - SELL ASSET UUID (backward compat)
                "SGD", // CashCurrency - selling SGD
                "2026-01-21", // Date
                "4600", // Quantity - amount of USD bought
                "", // BaseRate
                "USD", // TradeCurrency - USD
                "1", // Price
                "0", // Fees
                "", // PortfolioRate
                "", // TradeAmount
                "-5958.77", // CashAmount - SGD sold (negative)
                "Buy USD/Sell SGD", // Comments
                "SETTLED" // Status
            )

        val request =
            TrustedTrnImportRequest(
                portfolio = portfolio,
                row = csvRow
            )

        val result = bcRowAdapter.transform(request)

        assertThat(result).isNotNull
        assertThat(result.trnType).isEqualTo(TrnType.FX_BUY)
        assertThat(result.assetId).isEqualTo(scbUsdAccount.id)
        // The key assertion: cashAssetId should be the sell asset's ID
        assertThat(result.cashAssetId).isEqualTo(scbSgdAccount.id)
    }

    /**
     * Test that FX_BUY works when the code does NOT have the owner prefix.
     * This is what the frontend SHOULD send (just "SCB-USD").
     */
    @Test
    fun `should handle FX_BUY with clean asset code without owner prefix`() {
        val cleanCode = "SCB-USD"

        // Mock asset resolution - the backend should find the asset by short code
        lenient()
            .`when`(
                ais.resolveAsset(
                    AssetInput(
                        market = "PRIVATE",
                        code = cleanCode, // Clean code without owner prefix
                        name = "",
                        owner = ownerId
                    )
                )
            ).thenReturn(scbUsdAccount)

        val csvRow =
            listOf(
                "20260121", // Batch
                "", // CallerId
                "FX_BUY", // Type
                "PRIVATE", // Market
                cleanCode, // Code - just "SCB-USD" without owner prefix
                "", // Name
                "", // CashAccount
                "SGD", // CashCurrency
                "2026-01-21", // Date
                "4600", // Quantity
                "", // BaseRate
                cleanCode, // TradeCurrency
                "1", // Price
                "0", // Fees
                "", // PortfolioRate
                "", // TradeAmount
                "-5958.77", // CashAmount
                "Buy USD/Sell SGD", // Comments
                "SETTLED" // Status
            )

        val request =
            TrustedTrnImportRequest(
                portfolio = portfolio,
                row = csvRow
            )

        val result = bcRowAdapter.transform(request)

        assertThat(result).isNotNull
        assertThat(result.trnType).isEqualTo(TrnType.FX_BUY)
        assertThat(result.assetId).isEqualTo(scbUsdAccount.id)
    }
}