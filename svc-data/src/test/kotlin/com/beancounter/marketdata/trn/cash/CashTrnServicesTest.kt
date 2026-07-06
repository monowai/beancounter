package com.beancounter.marketdata.trn.cash

import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.Broker
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Market
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.Constants.Companion.CASH_MARKET
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.Constants.Companion.nzdCashBalance
import com.beancounter.marketdata.Constants.Companion.systemUser
import com.beancounter.marketdata.Constants.Companion.usdCashBalance
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.broker.BrokerSettlementAccount
import com.beancounter.marketdata.broker.BrokerSettlementAccountRepository
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
    private val brokerSettlementAccountRepository = Mockito.mock(BrokerSettlementAccountRepository::class.java)
    private val cashTrnServices =
        CashTrnServices(assetFinder, assetService, currencyService, brokerSettlementAccountRepository)

    private val broker = Broker(id = "ib-broker", name = "Interactive Brokers", owner = systemUser)
    private val ibrkUsd =
        Asset(
            code = "IBRK-USD",
            id = "ibrk-usd-id",
            name = "IBRK USD",
            market = Market("PRIVATE", USD.code),
            priceSymbol = USD.code,
            category = "CASH",
            assetCategory = AssetCategory("CASH", "Cash")
        )
    private val ibrkSettlement =
        BrokerSettlementAccount(broker = broker, currencyCode = USD.code, account = ibrkUsd)

    @Test
    fun `should settle to broker settlement account when broker present and no explicit cash account`() {
        Mockito
            .`when`(brokerSettlementAccountRepository.findByBrokerIdAndCurrencyCode(broker.id, USD.code))
            .thenReturn(ibrkSettlement)
        assertThat(
            cashTrnServices.getCashAsset(
                trnType = TrnType.BUY,
                cashAccountCode = null,
                cashCurrency = USD.code,
                ownerId = systemUser.id,
                brokerId = broker.id
            )
        ).isEqualTo(ibrkUsd)
        // Broker tier resolved — generic currency fallback must not fire
        Mockito.verifyNoInteractions(assetService)
    }

    @Test
    fun `should fall back to generic balance when broker has no settlement account for currency`() {
        Mockito
            .`when`(brokerSettlementAccountRepository.findByBrokerIdAndCurrencyCode(broker.id, USD.code))
            .thenReturn(null)
        Mockito
            .`when`(assetService.findOrCreate(AssetInput("CASH", USD.code)))
            .thenReturn(usdCashBalance)
        assertThat(
            cashTrnServices.getCashAsset(
                trnType = TrnType.BUY,
                cashAccountCode = null,
                cashCurrency = USD.code,
                ownerId = systemUser.id,
                brokerId = broker.id
            )
        ).isEqualTo(usdCashBalance)
    }

    @Test
    fun `should settle FX_BUY to broker settlement account for the sell-leg currency`() {
        Mockito
            .`when`(brokerSettlementAccountRepository.findByBrokerIdAndCurrencyCode(broker.id, USD.code))
            .thenReturn(ibrkSettlement)
        assertThat(
            cashTrnServices.getCashAsset(
                trnType = TrnType.FX_BUY,
                cashAccountCode = null,
                cashCurrency = USD.code,
                ownerId = systemUser.id,
                brokerId = broker.id
            )
        ).isEqualTo(ibrkUsd)
    }

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