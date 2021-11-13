package com.beancounter.marketdata.trn

import com.beancounter.client.ingest.AssetIngestService
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.Constants.Companion.USD
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

private val cdna = "CDNA"

/**
 * BC Row Adapter tests for handling various assertions around transformations.
 */
class BcRowAdapterTest {
    val ais: AssetIngestService = Mockito.mock(AssetIngestService::class.java)
    val assetService = Mockito.mock(AssetService::class.java)
    val currencyService = Mockito.mock(CurrencyService::class.java)
    val cashServices = CashServices(assetService, currencyService)

    @BeforeEach
    fun setupMocks() {
        Mockito.`when`(ais.resolveAsset("NASDAQ", cdna, "Caredx"))
            .thenReturn(
                Asset(
                    code = cdna,
                    market = NASDAQ
                )
            )
        Mockito.`when`(assetService.find("${USD.code} Balance")).thenReturn(usdCashBalance)
    }

    @Test
    fun trimmedCsvInputValues() {
        // Input has been formatted with extraneous spaces.
        val values = "BC,USX,Kt-1jW3x1g,BUY,NASDAQ,$cdna,Caredx," +
            "USD Balance,USD,2021-08-11,200.000000,NZD,1.000000,USD,77.780000,0.00,1.386674,2000.00,-2000.00,"

        // BC will receive data in the same manner
        val trustedTrnImportRequest = TrustedTrnImportRequest(
            Portfolio("CSV"),
            values.split(","),
        )

        val rowAdapter = BcRowAdapter(ais, cashServices = cashServices)

        val result = rowAdapter.transform(trustedTrnImportRequest)
        // Transformation should still resolve without extra spaces.
        assertThat(result)
            .hasFieldOrPropertyWithValue("trnType", TrnType.BUY)
            .hasFieldOrPropertyWithValue("tradeCurrency", "USD")
            .hasFieldOrPropertyWithValue("comments", "")
            .hasFieldOrPropertyWithValue("tradeDate", DateUtils().getOrThrow("2021-08-11"))
            .hasFieldOrPropertyWithValue("quantity", BigDecimal(200))
            .hasFieldOrPropertyWithValue("assetId", cdna)
            .hasFieldOrPropertyWithValue("cashAssetId", usdCashBalance.code)
            .hasFieldOrPropertyWithValue("cashAmount", BigDecimal("-2000")) // Nothing sent, so nothing computed
            .hasFieldOrPropertyWithValue("tradeAmount", BigDecimal("2000"))
    }

    @Test
    fun forwardTradeDateFails() {
        val tomorrow = LocalDate.now().atStartOfDay().plusDays(1)
        val values = "BC      ,ee,ff-r5w,BUY ,NYSE  ,QQQ ,Invesco QQQ Trust Series 1,USD Balance,USD         ," +
            "$tomorrow,1.000000,SGD         ,0.740494,USD          ,308.110000,0.00,1.000000     ,309.11     " +
            ",-309.11   ,"
        val trustedTrnImportRequest = TrustedTrnImportRequest(
            Portfolio("CSV"),
            values.split(","),
        )

        val rowAdapter = BcRowAdapter(ais, cashServices = cashServices)
        assertThrows(BusinessException::class.java) {
            rowAdapter.transform(trustedTrnImportRequest)
        }
    }
}
