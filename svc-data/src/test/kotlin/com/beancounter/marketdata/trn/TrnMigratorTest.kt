package com.beancounter.marketdata.trn

import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.fx.FxRateService
import com.beancounter.marketdata.fx.fxrates.EcbService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.math.BigDecimal

internal class TrnMigratorTest {
    private var assetService = Mockito.mock(AssetService::class.java)

    private var ecbService = Mockito.mock(EcbService::class.java)

    private var currencyService = Mockito.mock(CurrencyService::class.java)

    private var fxRateService = FxRateService(ecbService, currencyService)
    private var trnMigrator = TrnMigrator(fxRateService)
    private val tradeDateStr = "2021-11-11"
    val tradeDate = DateUtils().getFormattedDate(tradeDateStr)

    @BeforeEach
    fun setUp() {
        Mockito
            .`when`(assetService.findOrCreate(AssetInput("CASH", NZD.code)))
            .thenReturn(Constants.nzdCashBalance)
        Mockito.`when`(currencyService.getCode(NZD.code)).thenReturn(NZD)
        Mockito.`when`(currencyService.getCode(USD.code)).thenReturn(USD)
        Mockito.`when`(ecbService.getRates(tradeDateStr)).thenReturn(
            listOf(
                FxRate(USD, NZD, BigDecimal("2.00"), tradeDateStr),
                FxRate(USD, USD, BigDecimal("1.00"), tradeDateStr),
            ),
        )
    }

    @Test
    fun upgrade() {
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
                portfolio = Portfolio("test", currency = NZD, base = USD),
                version = "2",
            )
        trnV1.callerRef = CallerRef("ABC", "DEF", "GHI")
        val trnV2 = trnMigrator.upgrade(trnV1)
        assertThat(trnV2)
            .hasFieldOrPropertyWithValue("version", "3")
            .hasFieldOrPropertyWithValue("tradeCashRate", BigDecimal("2.00"))
            .hasFieldOrPropertyWithValue("tradeBaseRate", BigDecimal.ONE)
            .hasFieldOrPropertyWithValue("tradePortfolioRate", BigDecimal("2.00"))
            .hasFieldOrPropertyWithValue("cashAsset", Constants.nzdCashBalance)
            .hasFieldOrPropertyWithValue("cashCurrency", NZD)
    }
}
