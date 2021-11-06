package com.beancounter.marketdata.trn

import com.beancounter.client.ingest.AssetIngestService
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
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
class RowAdapaterTest {
    val ais: AssetIngestService = Mockito.mock(AssetIngestService::class.java)

    @BeforeEach
    fun setupMocks() {
        Mockito.`when`(ais.resolveAsset("NASDAQ", "CDNA", ""))
            .thenReturn(Asset("123", "CDNA", "Any Name", Market("NASDAQ")))
    }

    @Test
    fun trimmedCsvInputValues() {
        // Input has been formatted with extraneous spaces.
        val values = "BC      ,USX                   ,                      ,BUY ,NASDAQ,CDNA," +
            "                                             ,2021-08-11,20.000000  ,,,USD          ," +
            "77.78     ,0    ,0            ,1556.60    ,"

        // BC will receive data in the same manner
        val trustedTrnImportRequest = TrustedTrnImportRequest(
            Portfolio("CSV"),
            values.split(","),
        )

        val rowAdapter = BcRowAdapter(ais)

        val result = rowAdapter.transform(trustedTrnImportRequest)
        // Transformation should still resolve without extra spaces.
        assertThat(result)
            .hasFieldOrPropertyWithValue("trnType", TrnType.BUY)
            .hasFieldOrPropertyWithValue("tradeCurrency", "USD")
            .hasFieldOrPropertyWithValue("comments", "")
            .hasFieldOrPropertyWithValue("tradeDate", DateUtils().getOrThrow("2021-08-11"))
            .hasFieldOrPropertyWithValue("quantity", BigDecimal(20))
            .hasFieldOrPropertyWithValue("assetId", "123")
    }

    @Test
    fun forwardTradeDateFails() {
        val tomorrow = LocalDate.now().atStartOfDay().plusDays(1)
        val values = "BC      ,USX                   ,                      ,BUY ,NASDAQ,CDNA," +
            "                                             ,$tomorrow,20.000000  ,,,USD          ," +
            "77.78     ,0    ,0            ,1556.60    ,"
        val trustedTrnImportRequest = TrustedTrnImportRequest(
            Portfolio("CSV"),
            values.split(","),
        )

        val rowAdapter = BcRowAdapter(ais)
        assertThrows(BusinessException::class.java) {
            rowAdapter.transform(trustedTrnImportRequest)
        }
    }
}
