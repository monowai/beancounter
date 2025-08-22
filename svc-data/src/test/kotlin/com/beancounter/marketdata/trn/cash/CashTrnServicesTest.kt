package com.beancounter.marketdata.trn.cash

import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.Constants.Companion.CASH_MARKET
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.Constants.Companion.nzdCashBalance
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.trn.CashTrnServices
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.math.BigDecimal

/**
 * Cash can be a tricky beast. Abstract out specific cash handling routines to allow for new uses cases.
 */
internal class CashTrnServicesTest {
    private val nzCashAssetId = "${NZD.code} Cash"
    private val usCashAssetId = "${USD.code} Cash"

    private val assetFinder = Mockito.mock(AssetFinder::class.java)
    private val assetService = Mockito.mock(AssetService::class.java)
    private val currencyService = Mockito.mock(CurrencyService::class.java)
    private val cashTrnServices = CashTrnServices(assetFinder, assetService, currencyService)

    @Test
    fun is_CashBalanceFromTradeCurrency() {
        // Trade Currency, but no defined Cash Asset, resolves to a generic Balance asset.
        val trnInput =
            TrnInput(
                callerRef = CallerRef(),
                assetId = MSFT.code,
                trnType = TrnType.BUY,
                cashCurrency = NZD.code,
                tradeAmount = BigDecimal(5000),
                price = BigDecimal.ONE
            )

        Mockito
            .`when`(
                assetService.findOrCreate(
                    AssetInput(
                        CASH_MARKET.code,
                        NZD.code
                    )
                )
            ).thenReturn(nzdCashBalance)
        assertThat(cashTrnServices.getCashAsset(trnInput.trnType, trnInput.cashAssetId, trnInput.cashCurrency))
            .isEqualTo(nzdCashBalance)
    }

    @Test
    fun is_CashBalanceFromTradeCurrencyWithBlankAsset() {
        // Trade Currency, but no defined Cash Asset, resolves to a generic Balance asset.
        val trnInput =
            TrnInput(
                callerRef = CallerRef(),
                assetId = MSFT.code,
                cashAssetId = "",
                trnType = TrnType.BUY,
                cashCurrency = NZD.code,
                tradeAmount = BigDecimal(5000),
                price = BigDecimal.ONE
            )

        Mockito
            .`when`(
                assetService.findOrCreate(
                    AssetInput(
                        "CASH",
                        NZD.code
                    )
                )
            ).thenReturn(nzdCashBalance)
        assertThat(cashTrnServices.getCashAsset(trnInput.trnType, trnInput.cashAssetId, trnInput.cashCurrency))
            .isEqualTo(nzdCashBalance)
    }

    @Test
    fun is_SuppliedCashOverridingCalculatedCash() {
        val debitInput =
            TrnInput(
                callerRef = CallerRef(),
                assetId = MSFT.code,
                cashAssetId = nzCashAssetId,
                trnType = TrnType.BUY,
                tradeAmount = BigDecimal(5000),
                // Caller knows best
                cashAmount = BigDecimal("-2222.333"),
                price = BigDecimal.ONE,
                tradeCashRate = BigDecimal.ONE
            )

        assertThat(cashTrnServices.getCashImpact(debitInput)).isEqualTo(
            BigDecimal("-2222.333")
        ) // Fx of 1.00
    }

    @Test
    fun isCashDebitedForBuy() {
        val debitInput =
            TrnInput(
                callerRef = CallerRef(),
                assetId = MSFT.code,
                cashAssetId = nzCashAssetId,
                trnType = TrnType.BUY,
                tradeAmount = BigDecimal(5000),
                price = BigDecimal.ONE,
                tradeCashRate = BigDecimal.ONE
            )
        assertThat(cashTrnServices.getCashImpact(debitInput)).isEqualTo(
            BigDecimal("-5000.00")
        )
    }

    @Test
    fun isCashCreditedForSell() {
        val creditInput =
            TrnInput(
                callerRef = CallerRef(),
                assetId = MSFT.code,
                cashAssetId = nzCashAssetId,
                trnType = TrnType.SELL,
                tradeAmount = BigDecimal(5000),
                price = BigDecimal.ONE,
                tradeCashRate = BigDecimal.ONE
            )
        assertThat(cashTrnServices.getCashImpact(creditInput)).isEqualTo(
            BigDecimal("5000.00")
        )
    }

    @Test
    fun isCashCreditedForDeposit() {
        val depositInput =
            TrnInput(
                callerRef = CallerRef(),
                assetId = nzCashAssetId,
                trnType = TrnType.DEPOSIT,
                tradeAmount = BigDecimal(5000),
                price = BigDecimal.ONE,
                tradeCashRate = BigDecimal.ONE
            )
        assertThat(cashTrnServices.getCashImpact(depositInput)).isEqualTo(
            BigDecimal("5000.00")
        )
    }

    @Test
    fun isCashDebitedForWithdrawal() {
        val withdrawalInput =
            TrnInput(
                callerRef = CallerRef(),
                assetId = nzCashAssetId,
                trnType = TrnType.WITHDRAWAL,
                tradeAmount = BigDecimal(5000),
                price = BigDecimal.ONE,
                tradeCashRate = BigDecimal.ONE
            )
        assertThat(cashTrnServices.getCashImpact(withdrawalInput)).isEqualTo(
            BigDecimal("-5000.00")
        )
    }

    @Test
    fun isCashDebitedForFxBuy() {
        val fxBuyInput =
            TrnInput(
                callerRef = CallerRef(),
                assetId = usCashAssetId,
                cashAssetId = nzCashAssetId,
                trnType = TrnType.FX_BUY,
                tradeAmount = BigDecimal(5000),
                price = BigDecimal.ONE,
                tradeCashRate = BigDecimal.ONE
            )
        assertThat(cashTrnServices.getCashImpact(fxBuyInput)).isEqualTo(
            BigDecimal("-5000.00")
        )
    }

    @Test
    fun isCashImpactZeroForBalance() {
        val balanceInput =
            TrnInput(
                callerRef = CallerRef(),
                assetId = nzCashAssetId,
                trnType = TrnType.BALANCE,
                tradeAmount = BigDecimal(5000),
                price = BigDecimal.ONE,
                tradeCashRate = BigDecimal.ONE
            )
        assertThat(cashTrnServices.getCashImpact(balanceInput)).isEqualTo(
            BigDecimal.ZERO
        )
    }
}