package com.beancounter.marketdata.trn

import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.math.BigDecimal

internal class TrnMigratorTest {
    private var assetService = Mockito.mock(AssetService::class.java)

    private var fxRateService = Mockito.mock(FxRateService::class.java)

    private var currencyService = Mockito.mock(CurrencyService::class.java)

    private var cashServices = CashServices(assetService, currencyService)

    private var trnMigrator = TrnMigrator(cashServices, fxRateService)

    private val pair = IsoCurrencyPair(USD.code, NZD.code)
    private val tradeDateStr = "2021-11-11"
    val tradeDate = DateUtils().getDate(tradeDateStr)

    @BeforeEach
    fun setUp() {
        Mockito.`when`(assetService.find("CASH", "${NZD.code} Balance"))
            .thenReturn(Constants.nzdCashBalance)
        Mockito.`when`(currencyService.getCode(NZD.code)).thenReturn(NZD)
        Mockito.`when`(currencyService.getCode(USD.code)).thenReturn(USD)
        Mockito.`when`(fxRateService.getRates(FxRequest(tradeDateStr, arrayListOf(pair)))).thenReturn(
            FxResponse(
                FxPairResults(mapOf(Pair(pair, FxRate(USD, NZD, BigDecimal("3.00"), tradeDateStr))))
            )
        )
    }

    @Test
    fun upgrade() {
        val trnV1 = Trn(
            trnType = TrnType.BUY,
            version = "1",
            asset = MSFT,
            tradeDate = tradeDate,
            tradeCurrency = USD,
            cashCurrency = NZD,
            quantity = BigDecimal("1.0"),
            price = BigDecimal("1.0"),
            tradeAmount = BigDecimal("1000.00")
        )
        trnV1.callerRef = CallerRef("ABC", "DEF", "GHI")
        val trnV2 = trnMigrator.upgrade(trnV1)
        assertThat(trnV2)
            .hasFieldOrPropertyWithValue("version", "2")
            .hasFieldOrPropertyWithValue("cashAmount", BigDecimal("-3000.00"))
            .hasFieldOrPropertyWithValue("tradeCashRate", BigDecimal("3.00"))
            .hasFieldOrPropertyWithValue("cashAsset", Constants.nzdCashBalance)
            .hasFieldOrPropertyWithValue("cashCurrency", null)
    }
}
