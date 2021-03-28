package com.beancounter.client

import com.beancounter.client.ingest.AssetIngestService
import com.beancounter.client.ingest.TrnAdapter
import com.beancounter.client.sharesight.ShareSightConfig
import com.beancounter.client.sharesight.ShareSightDividendAdapter
import com.beancounter.client.sharesight.ShareSightTradeAdapter
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.ImportFormat
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.common.utils.TradeCalculator
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import java.math.BigDecimal

@SpringBootTest(classes = [ShareSightConfig::class])
class TestAdapters {
    @Autowired
    private lateinit var shareSightConfig: ShareSightConfig

    @Autowired
    private lateinit var dateUtils: DateUtils

    @Autowired
    private lateinit var tradeCalculator: TradeCalculator

    @MockBean
    private lateinit var assetIngestService: AssetIngestService

    @Test
    fun is_DividendIllegalNumber() {
        val row: MutableList<String> = arrayListOf()

        row.add(ShareSightDividendAdapter.id, "1")
        row.add(ShareSightDividendAdapter.code, "market")
        row.add(ShareSightDividendAdapter.name, "name")
        row.add(ShareSightDividendAdapter.date, "date")
        row.add(ShareSightDividendAdapter.fxRate, "A.B")
        val request = TrustedTrnImportRequest(getPortfolio("TEST"), row, ImportFormat.SHARESIGHT)
        val dividendAdapter: TrnAdapter = ShareSightDividendAdapter(shareSightConfig, assetIngestService)
        Assertions.assertThrows(BusinessException::class.java) { dividendAdapter.from(request) }
    }

    @Test
    fun is_NullTrnTypeCorrect() {
        val row: List<String> = arrayListOf("", "", "", "null", "")

        val trustedTrnImportRequest = TrustedTrnImportRequest(
            portfolio = getPortfolio("TEST"),
            row = row,
            ImportFormat.SHARESIGHT
        )

        val tradeAdapter = ShareSightTradeAdapter(
            shareSightConfig,
            assetIngestService,
            dateUtils,
            tradeCalculator
        )
        Assertions.assertThrows(BusinessException::class.java) { tradeAdapter.from(trustedTrnImportRequest) }
    }

    @Test
    fun is_BlankTrnTypeCorrect() {
        val row: List<String> = arrayListOf("", "", "", "", "")
        val trustedTrnImportRequest = TrustedTrnImportRequest(
            getPortfolio("TEST"),
            row, ImportFormat.SHARESIGHT
        )
        val tradeAdapter = ShareSightTradeAdapter(
            shareSightConfig,
            assetIngestService,
            dateUtils,
            tradeCalculator
        )
        Assertions.assertThrows(BusinessException::class.java) { tradeAdapter.from(trustedTrnImportRequest) }
    }

    @Test
    fun is_ValidTradeRow() {
        val row: MutableList<String> = arrayListOf()
        row.add(ShareSightTradeAdapter.id, "1")
        row.add(ShareSightTradeAdapter.market, "market") // Header Row
        row.add(ShareSightTradeAdapter.code, "code")
        row.add(ShareSightTradeAdapter.name, "name")
        row.add(ShareSightTradeAdapter.type, "BUY")
        row.add(ShareSightTradeAdapter.date, "date")
        row.add(ShareSightTradeAdapter.quantity, "quantity")
        row.add(ShareSightTradeAdapter.price, "price")
        val shareSightTradeAdapter = ShareSightTradeAdapter(
            shareSightConfig,
            assetIngestService,
            dateUtils,
            tradeCalculator
        )
        org.assertj.core.api.Assertions.assertThat(shareSightTradeAdapter.isValid(row)).isTrue
    }

    @Test
    fun is_TradeAmountComputed() {
        val row: MutableList<String> = arrayListOf()
        row.add(ShareSightTradeAdapter.id, "1")
        row.add(ShareSightTradeAdapter.market, "NYSE") // Header Row
        row.add(ShareSightTradeAdapter.code, "ABC")
        row.add(ShareSightTradeAdapter.name, "name")
        row.add(ShareSightTradeAdapter.type, "BUY")
        row.add(ShareSightTradeAdapter.date, "23/11/2018")
        row.add(ShareSightTradeAdapter.quantity, "10")
        row.add(ShareSightTradeAdapter.price, "10.0")
        row.add(ShareSightTradeAdapter.brokerage, "5.0")
        row.add(ShareSightTradeAdapter.currency, "USD")
        row.add(ShareSightTradeAdapter.fxRate, "null")
        row.add(ShareSightTradeAdapter.value, "null")
        val shareSightTradeAdapter = ShareSightTradeAdapter(
            shareSightConfig,
            assetIngestService,
            dateUtils,
            tradeCalculator
        )
        Mockito.`when`(assetIngestService.resolveAsset("NYSE", "ABC"))
            .thenReturn(getAsset("NYSE", "ABC"))
        val result = shareSightTradeAdapter.from(
            TrustedTrnImportRequest(getPortfolio("TEST"), row, ImportFormat.SHARESIGHT)
        )
        org.assertj.core.api.Assertions.assertThat(result)
            .hasFieldOrPropertyWithValue("tradeAmount", BigDecimal("105.00"))
    }

    @Test
    fun is_ValidDividendRow() {
        val row: MutableList<String> = arrayListOf()
        row.add(ShareSightTradeAdapter.id, "1")
        row.add(ShareSightDividendAdapter.code, "code") // Header Row
        row.add(ShareSightDividendAdapter.name, "code")
        row.add(ShareSightDividendAdapter.date, "name")
        row.add(ShareSightDividendAdapter.fxRate, "1.0")
        row.add(ShareSightDividendAdapter.currency, "date")
        row.add(ShareSightDividendAdapter.net, "quantity")
        row.add(ShareSightDividendAdapter.tax, "tax")
        row.add(ShareSightDividendAdapter.gross, "gross")
        row.add(ShareSightDividendAdapter.comments, "comments")
        val dividendAdapter = ShareSightDividendAdapter(shareSightConfig, assetIngestService)
        org.assertj.core.api.Assertions.assertThat(dividendAdapter.isValid(row)).isTrue
    }
}
