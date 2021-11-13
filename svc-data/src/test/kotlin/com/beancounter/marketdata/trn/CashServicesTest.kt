package com.beancounter.marketdata.trn

import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.nzdCashBalance
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.currency.CurrencyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.math.BigDecimal

private val nzCashAssetId = "${NZD.code} Cash"

/**
 * Cash can be a trick beast. Abstract out specific cash handling routines to allow for new uses cases.
 */
internal class CashServicesTest {

    private val assetService = Mockito.mock(AssetService::class.java)
    private val currencyService = Mockito.mock(CurrencyService::class.java)
    private val cashServices = CashServices(assetService, currencyService)

    @Test
    fun resolveCashBalanceFromTradeCurrency() {
        // Trade Currency, but no defined Cash Asset, resolves to a generic Balance asset.
        val trnInput = TrnInput(
            callerRef = CallerRef(),
            assetId = MSFT.code,
            trnType = TrnType.BUY,
            cashCurrency = NZD.code,
            tradeAmount = BigDecimal(5000),
            price = BigDecimal.ONE
        )

        Mockito.`when`(assetService.find("CASH", "${NZD.code} BALANCE"))
            .thenReturn(nzdCashBalance)
        assertThat(cashServices.getCashAsset(trnInput))
            .isEqualTo(nzdCashBalance)
    }

    @Test
    fun isSuppliedCashOverridingCalculatedCash() {
        val debitInput = TrnInput(
            callerRef = CallerRef(),
            assetId = MSFT.code,
            cashAssetId = nzCashAssetId,
            trnType = TrnType.BUY,
            tradeAmount = BigDecimal(5000),
            cashAmount = BigDecimal("-2222.333"), // Caller knows best
            price = BigDecimal.ONE
        )

        assertThat(cashServices.getCashImpact(debitInput)).isEqualTo(BigDecimal("-2222.333")) // Fx of 1.00
    }

    @Test
    fun isCashDebitedForBuy() {
        val debitInput = TrnInput(
            callerRef = CallerRef(),
            assetId = MSFT.code,
            cashAssetId = nzCashAssetId,
            trnType = TrnType.BUY,
            tradeAmount = BigDecimal(5000),
            price = BigDecimal.ONE
        )
        assertThat(cashServices.getCashImpact(debitInput)).isEqualTo(BigDecimal("-5000.00")) // Fx of 1.00
    }

    @Test
    fun isCashCreditedForSell() {
        val creditInput = TrnInput(
            callerRef = CallerRef(),
            assetId = MSFT.code,
            cashAssetId = nzCashAssetId,
            trnType = TrnType.SELL,
            tradeAmount = BigDecimal(5000),
            price = BigDecimal.ONE
        )
        assertThat(cashServices.getCashImpact(creditInput)).isEqualTo(BigDecimal("5000.00")) // Fx of 1.00
    }

    @Test
    fun isCashIgnoredForSplit() {
        val splitInput = TrnInput(
            callerRef = CallerRef(),
            assetId = MSFT.code,
            cashAssetId = nzCashAssetId,
            trnType = TrnType.SPLIT,
            tradeAmount = BigDecimal(5000),
            price = BigDecimal.ONE
        )
        assertThat(cashServices.getCashImpact(splitInput)).isEqualTo(BigDecimal.ZERO) // Fx of 1.00
    }
}
