package com.beancounter.client

import com.beancounter.client.Constants.Companion.NYSE
import com.beancounter.client.Constants.Companion.USD
import com.beancounter.client.ingest.AssetIngestService
import com.beancounter.client.ingest.TrnAdapter
import com.beancounter.client.sharesight.ShareSightConfig
import com.beancounter.client.sharesight.ShareSightDividendAdapter
import com.beancounter.client.sharesight.ShareSightTradeAdapter
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.ImportFormat
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.common.utils.TradeCalculator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal

/**
 * Sharesight transaction adapter tests
 */
@SpringBootTest(classes = [ShareSightConfig::class])
class TestAdapters {
    @Autowired
    private lateinit var shareSightConfig: ShareSightConfig

    @Autowired
    private lateinit var dateUtils: DateUtils

    @Autowired
    private lateinit var tradeCalculator: TradeCalculator

    @MockitoBean
    private lateinit var assetIngestService: AssetIngestService

    @Test
    fun `should handle dividend illegal number`() {
        val row: MutableList<String> = arrayListOf()

        row.add(
            ShareSightDividendAdapter.ID,
            "1"
        )
        row.add(
            ShareSightDividendAdapter.CODE,
            "market"
        )
        row.add(
            ShareSightDividendAdapter.NAME,
            "name"
        )
        row.add(
            ShareSightDividendAdapter.DATE,
            "date"
        )
        row.add(
            ShareSightDividendAdapter.FX_RATE,
            "A.B"
        )
        val request =
            TrustedTrnImportRequest(
                getPortfolio(),
                importFormat = ImportFormat.SHARESIGHT,
                row = row
            )
        val dividendAdapter: TrnAdapter =
            ShareSightDividendAdapter(
                shareSightConfig,
                assetIngestService,
                dateUtils
            )
        Assertions.assertThrows(BusinessException::class.java) { dividendAdapter.from(request) }
    }

    @Test
    fun `should handle null transaction type correctly`() {
        val row: List<String> =
            arrayListOf(
                "",
                "",
                "",
                "null",
                ""
            )

        val trustedTrnImportRequest =
            TrustedTrnImportRequest(
                portfolio = getPortfolio(),
                importFormat = ImportFormat.SHARESIGHT,
                row = row
            )

        val tradeAdapter =
            ShareSightTradeAdapter(
                shareSightConfig,
                assetIngestService,
                dateUtils,
                tradeCalculator
            )
        Assertions.assertThrows(BusinessException::class.java) {
            tradeAdapter.from(
                trustedTrnImportRequest
            )
        }
    }

    @Test
    fun `should handle blank transaction type correctly`() {
        val row: List<String> =
            arrayListOf(
                "",
                "",
                "",
                "",
                ""
            )
        val trustedTrnImportRequest =
            TrustedTrnImportRequest(
                getPortfolio(),
                importFormat = ImportFormat.SHARESIGHT,
                row = row
            )
        val tradeAdapter =
            ShareSightTradeAdapter(
                shareSightConfig,
                assetIngestService,
                dateUtils,
                tradeCalculator
            )
        Assertions.assertThrows(BusinessException::class.java) {
            tradeAdapter.from(
                trustedTrnImportRequest
            )
        }
    }

    @Test
    fun `should validate trade row`() {
        val row: MutableList<String> = arrayListOf()
        row.add(
            ShareSightTradeAdapter.ID,
            "1"
        )
        row.add(
            ShareSightTradeAdapter.MARKET,
            "market"
        ) // Header Row
        row.add(
            ShareSightTradeAdapter.CODE,
            "code"
        )
        row.add(
            ShareSightTradeAdapter.NAME,
            "name"
        )
        row.add(
            ShareSightTradeAdapter.TYPE,
            "BUY"
        )
        row.add(
            ShareSightTradeAdapter.DATE,
            "date"
        )
        row.add(
            ShareSightTradeAdapter.QUANTITY,
            "quantity"
        )
        row.add(
            ShareSightTradeAdapter.PRICE,
            "price"
        )
        val shareSightTradeAdapter =
            ShareSightTradeAdapter(
                shareSightConfig,
                assetIngestService,
                dateUtils,
                tradeCalculator
            )
        assertThat(shareSightTradeAdapter.isValid(row)).isTrue
    }

    @Test
    fun `should compute trade amount`() {
        val row: MutableList<String> = arrayListOf()
        row.add(
            ShareSightTradeAdapter.ID,
            "1"
        )
        row.add(
            ShareSightTradeAdapter.MARKET,
            "NYSE"
        ) // Header Row
        row.add(
            ShareSightTradeAdapter.CODE,
            "ABC"
        )
        row.add(
            ShareSightTradeAdapter.NAME,
            "name"
        )
        row.add(
            ShareSightTradeAdapter.TYPE,
            "BUY"
        )
        row.add(
            ShareSightTradeAdapter.DATE,
            "23/11/2018"
        )
        row.add(
            ShareSightTradeAdapter.QUANTITY,
            "10"
        )
        row.add(
            ShareSightTradeAdapter.PRICE,
            "10.0"
        )
        row.add(
            ShareSightTradeAdapter.BROKERAGE,
            "5.0"
        )
        row.add(
            ShareSightTradeAdapter.CURRENCY,
            USD.code
        )
        row.add(
            ShareSightTradeAdapter.FX_RATE,
            "null"
        )
        row.add(
            ShareSightTradeAdapter.VALUE,
            "null"
        )
        val shareSightTradeAdapter =
            ShareSightTradeAdapter(
                shareSightConfig,
                assetIngestService,
                dateUtils,
                tradeCalculator
            )
        Mockito
            .`when`(
                assetIngestService.resolveAsset(
                    AssetInput(
                        NYSE.code,
                        "ABC"
                    )
                )
            ).thenReturn(
                getTestAsset(
                    NYSE,
                    "ABC"
                )
            )
        val result =
            shareSightTradeAdapter.from(
                TrustedTrnImportRequest(
                    getPortfolio(),
                    importFormat = ImportFormat.SHARESIGHT,
                    row = row
                )
            )
        assertThat(result)
            .hasFieldOrPropertyWithValue(
                "tradeAmount",
                BigDecimal("105.00")
            )
    }

    @Test
    fun `should validate dividend row`() {
        val row: MutableList<String> = arrayListOf()
        row.add(
            ShareSightTradeAdapter.ID,
            "1"
        )
        row.add(
            ShareSightDividendAdapter.CODE,
            "code"
        ) // Header Row
        row.add(
            ShareSightDividendAdapter.NAME,
            "code"
        )
        row.add(
            ShareSightDividendAdapter.DATE,
            "name"
        )
        row.add(
            ShareSightDividendAdapter.FX_RATE,
            "1.0"
        )
        row.add(
            ShareSightDividendAdapter.CURRENCY,
            "date"
        )
        row.add(
            ShareSightDividendAdapter.NET,
            "quantity"
        )
        row.add(
            ShareSightDividendAdapter.TAX,
            "tax"
        )
        row.add(
            ShareSightDividendAdapter.GROSS,
            "gross"
        )
        row.add(
            ShareSightDividendAdapter.COMMENTS,
            "comments"
        )
        val dividendAdapter =
            ShareSightDividendAdapter(
                shareSightConfig,
                assetIngestService,
                dateUtils
            )
        assertThat(dividendAdapter.isValid(row)).isTrue
    }
}