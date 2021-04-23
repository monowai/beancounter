package com.beancounter.client.integ

import com.beancounter.client.Constants.Companion.AUD
import com.beancounter.client.Constants.Companion.NZD
import com.beancounter.client.config.ClientConfig
import com.beancounter.client.sharesight.ShareSightConfig
import com.beancounter.client.sharesight.ShareSightFactory
import com.beancounter.client.sharesight.ShareSightRowAdapter
import com.beancounter.client.sharesight.ShareSightTradeAdapter
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.ImportFormat
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.TrnType
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
/**
 * Importing Sharesight data.
 */
internal class ShareSightTradeTest {

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
            tradeDate,
            quantity,
            price,
            brokerage,
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
            portfolio = getPortfolio("Test", NZD),
            row = row,
            importFormat = ImportFormat.SHARESIGHT
        )
        val trn = shareSightRowProcessor.transform(trustedTrnImportRequest)
        Assertions.assertThat(trn)
            .hasFieldOrPropertyWithValue("TrnType", TrnType.BUY)
            .hasFieldOrPropertyWithValue("quantity", BigDecimal(10))
            .hasFieldOrPropertyWithValue("fees", BigDecimal(brokerage)) // No FX Rate
            .hasFieldOrPropertyWithValue("price", BigDecimal(price))
            .hasFieldOrPropertyWithValue("comments", null)
            .hasFieldOrProperty("assetId")
            .hasFieldOrProperty("tradeDate")
    }

    @Test
    fun is_SplitTransactionTransformed() {
        val row = getRow("split", "0", "null")
        val portfolio = getPortfolio("Test", NZD)
        val trn = shareSightRowProcessor.transform(
            TrustedTrnImportRequest(
                portfolio,
                row, ImportFormat.SHARESIGHT
            )
        )
        Assertions.assertThat(trn)
            .hasFieldOrPropertyWithValue("callerRef.callerId", "1")
            .hasFieldOrPropertyWithValue("TrnType", TrnType.SPLIT)
            .hasFieldOrPropertyWithValue("quantity", BigDecimal(quantity))
            .hasFieldOrPropertyWithValue("price", BigDecimal(price))
            .hasFieldOrPropertyWithValue("tradeAmount", BigDecimal.ZERO)
            .hasFieldOrPropertyWithValue("comments", "Test Comment")
            .hasFieldOrPropertyWithValue("tradeCurrency", AUD.code)
            .hasFieldOrProperty("assetId")
            .hasFieldOrProperty("tradeDate")
    }

    @Test
    fun is_IllegalDateHandled() {
        val row = getRow("buy", "0.8988", "2097.85").toMutableList()
        row.add(ShareSightTradeAdapter.date, "$tradeDate'")
        assertThrows(
            BusinessException::class.java
        ) {
            shareSightRowProcessor.transform(
                TrustedTrnImportRequest(
                    getPortfolio("Test", NZD), row, ImportFormat.SHARESIGHT
                )
            )
        }
    }

    companion object {
        private const val price = "12.23"

        fun getRow(tranType: String, fxRate: String, tradeAmount: String): List<String> {
            return getRow("AMP", "ASX", tranType, fxRate, tradeAmount)
        }

        private const val brokerage = "12.99"

        private const val tradeDate = "21/01/2019"

        private const val quantity = "10"

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
            row.add(ShareSightTradeAdapter.date, tradeDate)
            row.add(ShareSightTradeAdapter.quantity, quantity)
            row.add(ShareSightTradeAdapter.price, price)
            row.add(ShareSightTradeAdapter.brokerage, brokerage)
            row.add(ShareSightTradeAdapter.currency, AUD.code)
            row.add(ShareSightTradeAdapter.fxRate, fxRate)
            row.add(ShareSightTradeAdapter.value, tradeAmount)
            row.add(ShareSightTradeAdapter.comments, "Test Comment")
            return row
        }
    }
}
