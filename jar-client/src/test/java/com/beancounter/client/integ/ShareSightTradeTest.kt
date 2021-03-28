package com.beancounter.client.integ

import com.beancounter.client.config.ClientConfig
import com.beancounter.client.sharesight.ShareSightConfig
import com.beancounter.client.sharesight.ShareSightFactory
import com.beancounter.client.sharesight.ShareSightRowAdapter
import com.beancounter.client.sharesight.ShareSightTradeAdapter
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.ImportFormat
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.CurrencyUtils
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal

/**
 * Sharesight Transaction to BC model.Transaction.
 *
 * @author mikeh
 * @since 2019-02-12
 */
@ActiveProfiles("test")
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:+:stubs:10999"]
)
@SpringBootTest(classes = [ShareSightConfig::class, ClientConfig::class])
internal class ShareSightTradeTest {
    private val currencyUtils = CurrencyUtils()

    @Autowired
    private lateinit var shareSightRowProcessor: ShareSightRowAdapter

    @Autowired
    private lateinit var shareSightFactory: ShareSightFactory

    @Test
    fun is_SplitTransformerFoundForRow() {
        val row: List<String> = mutableListOf(
            "1",
            "ASX",
            "SLB",
            "Test Asset",
            "split",
            "21/01/2019",
            "10",
            "12.23",
            "12.99",
            "AUD"
        )
        val trnAdapter = shareSightFactory.adapter(row)
        Assertions.assertThat(trnAdapter).isInstanceOf(
            ShareSightTradeAdapter::class.java
        )
    }

    @Test
    fun is_RowWithNoCommentTransformed() {
        val row = getRow("buy", "0.8988", "2097.85").toMutableList()
        row[ShareSightTradeAdapter.comments] = "null"
        val trustedTrnImportRequest = TrustedTrnImportRequest(
            portfolio = getPortfolio("Test", currencyUtils.getCurrency("NZD")),
            row = row,
            importFormat = ImportFormat.SHARESIGHT
        )
        val trn = shareSightRowProcessor.transform(trustedTrnImportRequest)
        Assertions.assertThat(trn)
            .hasFieldOrPropertyWithValue("TrnType", TrnType.BUY)
            .hasFieldOrPropertyWithValue("quantity", BigDecimal(10))
            .hasFieldOrPropertyWithValue("fees", BigDecimal("12.99")) // No FX Rate
            .hasFieldOrPropertyWithValue("price", BigDecimal("12.23"))
            .hasFieldOrPropertyWithValue("comments", null)
            .hasFieldOrProperty("assetId")
            .hasFieldOrProperty("tradeDate")
    }

    @Test
    fun is_SplitTransactionTransformed() {
        val row = getRow("split", "0", "null")
        val portfolio = getPortfolio("Test", currencyUtils.getCurrency("NZD"))
        val trn = shareSightRowProcessor.transform(
            TrustedTrnImportRequest(
                portfolio,
                row, ImportFormat.SHARESIGHT
            )
        )
        Assertions.assertThat(trn)
            .hasFieldOrPropertyWithValue("callerRef.callerId", "1")
            .hasFieldOrPropertyWithValue("TrnType", TrnType.SPLIT)
            .hasFieldOrPropertyWithValue("quantity", BigDecimal("10"))
            .hasFieldOrPropertyWithValue("price", BigDecimal("12.23"))
            .hasFieldOrPropertyWithValue("tradeAmount", BigDecimal.ZERO)
            .hasFieldOrPropertyWithValue("comments", "Test Comment")
            .hasFieldOrPropertyWithValue("tradeCurrency", "AUD")
            .hasFieldOrProperty("assetId")
            .hasFieldOrProperty("tradeDate")
    }

    @Test
    fun is_IllegalDateHandled() {
        val row = getRow("buy", "0.8988", "2097.85").toMutableList()
        row.add(ShareSightTradeAdapter.date, "21/01/2019'")
        assertThrows(
            BusinessException::class.java
        ) {
            shareSightRowProcessor.transform(
                TrustedTrnImportRequest(
                    getPortfolio("Test", currencyUtils.getCurrency("NZD")), row, ImportFormat.SHARESIGHT
                )
            )
        }
    }

    companion object {
        fun getRow(tranType: String, fxRate: String, tradeAmount: String): List<String> {
            return getRow("AMP", "ASX", tranType, fxRate, tradeAmount)
        }

        fun getRow(
            code: String,
            market: String,
            tranType: String,
            fxRate: String,
            tradeAmount: String,
        ): List<String> {
            val row: MutableList<String> = mutableListOf()
            row.add(ShareSightTradeAdapter.id, "1")
            row.add(ShareSightTradeAdapter.market, market)
            row.add(ShareSightTradeAdapter.code, code)
            row.add(ShareSightTradeAdapter.name, "Test Asset")
            row.add(ShareSightTradeAdapter.type, tranType)
            row.add(ShareSightTradeAdapter.date, "21/01/2019")
            row.add(ShareSightTradeAdapter.quantity, "10")
            row.add(ShareSightTradeAdapter.price, "12.23")
            row.add(ShareSightTradeAdapter.brokerage, "12.99")
            row.add(ShareSightTradeAdapter.currency, "AUD")
            row.add(ShareSightTradeAdapter.fxRate, fxRate)
            row.add(ShareSightTradeAdapter.value, tradeAmount)
            row.add(ShareSightTradeAdapter.comments, "Test Comment")
            return row
        }
    }
}
