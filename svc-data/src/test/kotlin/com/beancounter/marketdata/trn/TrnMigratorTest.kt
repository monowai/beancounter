package com.beancounter.marketdata.trn

import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.CASH_MARKET
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.US
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.currency.CurrencyConfig
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.fx.FxRateRepository
import com.beancounter.marketdata.fx.FxRateService
import com.beancounter.marketdata.fx.fxrates.EcbService
import com.beancounter.marketdata.markets.MarketService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.math.BigDecimal

internal class TrnMigratorTest {
    private var assetService = Mockito.mock(AssetService::class.java)

    private var ecbService = Mockito.mock(EcbService::class.java)

    private var currencyService = Mockito.mock(CurrencyService::class.java)

    private var currencyConfig = Mockito.mock(CurrencyConfig::class.java)

    private var marketService = Mockito.mock(MarketService::class.java)

    private var fxRateRepository = Mockito.mock(FxRateRepository::class.java)

    private var fxRateService =
        FxRateService(
            ecbService,
            currencyService,
            marketService,
            fxRateRepository = fxRateRepository
        )
    private var trnMigrator = TrnMigrator(fxRateService)
    private val tradeDateStr = "2021-11-11"
    val tradeDate = DateUtils().getFormattedDate(tradeDateStr)

    @BeforeEach
    fun setUp() {
        Mockito.`when`(marketService.getMarket(US.code)).thenReturn(US)
        Mockito
            .`when`(
                assetService.findOrCreate(
                    AssetInput(
                        CASH_MARKET.code,
                        NZD.code
                    )
                )
            ).thenReturn(Constants.nzdCashBalance)
        Mockito.`when`(currencyService.getCode(NZD.code)).thenReturn(NZD)
        Mockito.`when`(currencyService.getCode(USD.code)).thenReturn(USD)
        Mockito.`when`(currencyService.currencyConfig).thenReturn(currencyConfig)
        Mockito.`when`(currencyConfig.baseCurrency).thenReturn(USD)
        Mockito.`when`(ecbService.getRates(tradeDateStr)).thenReturn(
            listOf(
                FxRate(
                    from = USD,
                    to = NZD,
                    rate = BigDecimal("2.00"),
                    date = tradeDate
                ),
                FxRate(
                    from = USD,
                    to = USD,
                    rate = BigDecimal("1.00"),
                    date = tradeDate
                )
            )
        )
    }

    @Test
    fun `should upgrade v2 transactions through v3 to v4`() {
        val trnV1 =
            Trn(
                id = "TrnV1",
                trnType = TrnType.BUY,
                tradeDate = tradeDate,
                asset = MSFT,
                quantity = BigDecimal("1.0"),
                price = BigDecimal("1.0"),
                tradeAmount = BigDecimal("1000.00"),
                tradeCurrency = USD,
                cashAsset = Constants.nzdCashBalance,
                cashCurrency = NZD,
                portfolio =
                    Portfolio(
                        "test",
                        currency = NZD,
                        base = USD
                    ),
                version = "2"
            )
        trnV1.callerRef =
            CallerRef(
                "ABC",
                "DEF",
                "GHI"
            )
        val trnV2 = trnMigrator.upgrade(trnV1)
        assertThat(trnV2)
            .hasFieldOrPropertyWithValue(
                "version",
                "4"
            ).hasFieldOrPropertyWithValue(
                "tradeCashRate",
                BigDecimal("2.00")
            ).hasFieldOrPropertyWithValue(
                "tradeBaseRate",
                BigDecimal.ONE
            ).hasFieldOrPropertyWithValue(
                "tradePortfolioRate",
                BigDecimal("2.00")
            ).hasFieldOrPropertyWithValue(
                "cashAsset",
                Constants.nzdCashBalance
            ).hasFieldOrPropertyWithValue(
                "cashCurrency",
                NZD
            )
    }

    @Test
    fun `should upgrade v3 transactions to v4 for cash cost tracking`() {
        // Test v3 → v4 upgrade (cash cost tracking enablement)
        val trnV3 =
            Trn(
                id = "TrnV3",
                trnType = TrnType.FX_BUY,
                tradeDate = tradeDate,
                asset = Constants.usdCashBalance,
                quantity = BigDecimal("1000.00"),
                price = BigDecimal("1.0"),
                tradeAmount = BigDecimal("1000.00"),
                tradeCurrency = USD,
                cashAsset = Constants.nzdCashBalance,
                cashCurrency = NZD,
                tradeCashRate = BigDecimal("1.5"),
                tradeBaseRate = BigDecimal("1.0"),
                tradePortfolioRate = BigDecimal("1.5"),
                portfolio =
                    Portfolio(
                        "test",
                        currency = NZD,
                        base = USD
                    ),
                version = "3" // v3: Has FX rates
            )

        val trnV4 = trnMigrator.upgrade(trnV3)

        assertThat(trnV4)
            .hasFieldOrPropertyWithValue("version", "4") // Upgraded to v4: Cash cost tracking
            .hasFieldOrPropertyWithValue("tradeCashRate", BigDecimal("1.5")) // FX rates preserved
            .hasFieldOrPropertyWithValue("tradeBaseRate", BigDecimal("1.0"))
            .hasFieldOrPropertyWithValue("tradePortfolioRate", BigDecimal("1.5"))
    }

    @Test
    fun `should not upgrade v4 transactions as they are already current`() {
        // Test that v4 transactions are not modified
        val trnV4 =
            Trn(
                id = "TrnV4",
                trnType = TrnType.BUY,
                tradeDate = tradeDate,
                asset = MSFT,
                quantity = BigDecimal("1.0"),
                price = BigDecimal("100.0"),
                tradeAmount = BigDecimal("100.00"),
                tradeCurrency = USD,
                portfolio =
                    Portfolio(
                        "test",
                        currency = USD,
                        base = USD
                    ),
                version = "4" // Already at v4
            )

        val result = trnMigrator.upgrade(trnV4)

        assertThat(result)
            .hasFieldOrPropertyWithValue("version", "4") // Should remain v4
            .isSameAs(trnV4) // Should return the same object (no changes)
    }

    @Test
    fun `should upgrade all transaction types from v3 to v4`() {
        // Test that non-cost-tracking transaction types still get v3→v4 upgrade
        // (the version change applies to all transactions, but cost tracking logic
        // in the position service only applies to specific types)
        val depositTrn =
            Trn(
                id = "DepositV3",
                trnType = TrnType.DEPOSIT,
                tradeDate = tradeDate,
                asset = Constants.usdCashBalance,
                quantity = BigDecimal("1000.00"),
                tradeCurrency = USD,
                portfolio =
                    Portfolio(
                        "test",
                        currency = USD,
                        base = USD
                    ),
                version = "3"
            )

        val result = trnMigrator.upgrade(depositTrn)

        assertThat(result)
            .hasFieldOrPropertyWithValue("version", "4") // All transactions get v4
    }
}