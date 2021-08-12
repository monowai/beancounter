package com.beancounter.marketdata.integ

import com.beancounter.client.ingest.AssetIngestService
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.trn.BcRowAdapter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.math.BigDecimal

/**
 * BC Row Adapter tests for handling various assertions around transformations.
 */
class RowAdapaterTest {

    @Test
    fun trimmedCsvInputValues() {
        // Input has been formatted with extraneous spaces.
        val values = "BC      ,USX                   ,                      ,BUY ,NASDAQ,CDNA," +
            "                                             ,2021-08-11,20.000000  ,USD          ," +
            "77.78     ,0    ,0            ,1556.60    ,"

        // BC will receive data in the same manner
        val trustedTrnImportRequest = TrustedTrnImportRequest(
            Portfolio("CSV"),
            values.split(","),
        )

        val ais = Mockito.mock(AssetIngestService::class.java)
        Mockito.`when`(ais.resolveAsset("NASDAQ", "CDNA", ""))
            .thenReturn(Asset("123", "CDNA", "Any Name", Market("NASDAQ")))
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
}
