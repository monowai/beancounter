package com.beancounter.marketdata.trn

import com.beancounter.client.ingest.AssetIngestService
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.CASH
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.Constants.Companion.nzdCashBalance
import com.beancounter.marketdata.Constants.Companion.usdCashBalance
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.currency.CurrencyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.math.BigDecimal
import java.time.LocalDate

/**
 * BC Row Adapter tests for handling various assertions around transformations.
 */
class BcRowAdapterTest {
    private val cdna = "CDNA"
    private val ais: AssetIngestService = Mockito.mock(AssetIngestService::class.java)
    val assetService: AssetService = Mockito.mock(AssetService::class.java)
    val currencyService: CurrencyService = Mockito.mock(CurrencyService::class.java)
    private val cashServices = CashServices(assetService, currencyService)
    private val rowAdapter = BcRowAdapter(ais, cashServices = cashServices)

    @BeforeEach
    fun setupMocks() {
        Mockito.`when`(ais.resolveAsset("NASDAQ", cdna, "Caredx"))
            .thenReturn(
                Asset(
                    code = cdna,
                    market = NASDAQ
                )
            )
        Mockito.`when`(ais.resolveAsset(CASH.code, NZD.code, ""))
            .thenReturn(nzdCashBalance)
        Mockito.`when`(ais.resolveAsset(CASH.code, USD.code, ""))
            .thenReturn(usdCashBalance)
        Mockito.`when`(assetService.find(CASH.code, NZD.code)).thenReturn(nzdCashBalance)
        Mockito.`when`(assetService.find(CASH.code, USD.code)).thenReturn(usdCashBalance)
        Mockito.`when`(assetService.find(USD.code)).thenReturn(usdCashBalance)
        Mockito.`when`(assetService.find(NZD.code)).thenReturn(nzdCashBalance)
    }

    private val propAssetId = "assetId"
    private val cashAssetId = "cashAssetId"
    private val tradeAmount = "tradeAmount"
    private val propQuantity = "quantity"
    private val propCashAmount = "cashAmount"

    @Test
    fun trimmedCsvInputValues() {
        val values = "BC  ,USX  ,Kt-1jW3x1g,BUY  ,NASDAQ  ,$cdna,Caredx," +
            "USD ,USD ,2021-08-11 ,200.000000  ,NZD  ,1.000000  ,USD  ,77.780000  ,0.00,1.386674  ,2000.00  ,-2000.00  ,"

// BC will receive data in the same manner
        val trustedTrnImportRequest = trustedTrnImportRequest(values)

        val result = rowAdapter.transform(trustedTrnImportRequest)
// Transformation should still resolve without extra spaces.
        assertThat(result)
            .hasFieldOrPropertyWithValue("trnType", TrnType.BUY)
            .hasFieldOrPropertyWithValue("tradeCurrency", "USD")
            .hasFieldOrPropertyWithValue("comments", "")
            .hasFieldOrPropertyWithValue("tradeDate", DateUtils().getOrThrow("2021-08-11"))
            .hasFieldOrPropertyWithValue(propQuantity, BigDecimal(200))
            .hasFieldOrPropertyWithValue(propAssetId, cdna)
            .hasFieldOrPropertyWithValue(cashAssetId, usdCashBalance.code)
            .hasFieldOrPropertyWithValue(propCashAmount, BigDecimal("-2000")) // Nothing sent, so nothing computed
            .hasFieldOrPropertyWithValue(tradeAmount, BigDecimal("2000"))
    }

    @Test
    fun forwardTradeDateFails() {
        val tomorrow = LocalDate.now().atStartOfDay().plusDays(1)
        val values = "BC,ee,ff-r5w,BUY ,NYSE,QQQ ,Invesco QQQ Trust Series 1,USD Balance,USD ," +
            "$tomorrow,1.000000,SGD ,0.740494,USD,308.110000,0.00,1.000000 ,309.11 " +
            ",-309.11 ,"

        val trustedTrnImportRequest = trustedTrnImportRequest(values)

        assertThrows(BusinessException::class.java) {
            rowAdapter.transform(trustedTrnImportRequest)
        }
    }

    @Test
    fun cashDeposit() {
        val values =
            "BC,abc,,DEPOSIT,CASH,NZD,,,NZD,2021-11-16,10000.00,NZD,,NZD,1.000000,0,1.000000,10000,,"

        val trustedTrnImportRequest = trustedTrnImportRequest(values)
        val trn = rowAdapter.transform(trustedTrnImportRequest)
        assertThat(trn)
            .hasFieldOrPropertyWithValue(propAssetId, nzdCashBalance.id)
            .hasFieldOrPropertyWithValue(cashAssetId, nzdCashBalance.id)
            .hasFieldOrPropertyWithValue(tradeAmount, BigDecimal("10000"))
    }

    @Test
    fun callerRef() {
        val values =
            "TEST,CALLER_REF,,DEPOSIT,CASH,NZD,,,NZD,2021-11-16,10000.00,NZD,,NZD,1.000000,0,1.000000,10000,,"

        val trustedTrnImportRequest = trustedTrnImportRequest(values)
        val trn = rowAdapter.transform(trustedTrnImportRequest)
        assertThat(trn.callerRef)
            .hasFieldOrPropertyWithValue("provider", "TEST")
            .hasFieldOrPropertyWithValue("batch", "CALLER_REF")
            .hasFieldOrProperty("callerId")
    }

    @Test
    fun fxBuyTrade() {
        // Buy USD, Sell NZD
        val values =
            "BC,abc,,FX_BUY,CASH,USD,,,NZD,2021-11-16,8359.43,NZD,,USD,1.000000,0 ,1.000000,8359.43,-10000.00,"

        val trustedTrnImportRequest = trustedTrnImportRequest(values)
        val trn = rowAdapter.transform(trustedTrnImportRequest)
        val amount = BigDecimal("8359.43")
        assertThat(trn)
            .hasFieldOrPropertyWithValue(propAssetId, usdCashBalance.id)
            .hasFieldOrPropertyWithValue(cashAssetId, nzdCashBalance.id)
            .hasFieldOrPropertyWithValue(propQuantity, amount)
            .hasFieldOrPropertyWithValue(tradeAmount, amount)
            .hasFieldOrPropertyWithValue(propCashAmount, BigDecimal("-10000"))
    }

    private fun trustedTrnImportRequest(values: String): TrustedTrnImportRequest =
        TrustedTrnImportRequest(
            Portfolio("CSV"),
            values.split(",")
        )
}
