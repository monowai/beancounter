package com.beancounter.client.integ

import com.beancounter.auth.TokenService
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
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import java.math.BigDecimal

/**
 * Sharesight Transaction to BC model.Transaction.
 *
 * @author mikeh
 * @since 2019-02-12
 */
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:+:stubs:10999"],
)
@SpringBootTest(classes = [ShareSightConfig::class, ClientConfig::class])
internal class ShareSightTradeTest {
    @Autowired
    private lateinit var shareSightRowProcessor: ShareSightRowAdapter

    @Autowired
    private lateinit var shareSightFactory: ShareSightFactory

    @MockBean
    private lateinit var tokenService: TokenService

    @Test
    fun is_SplitTransformerFoundForRow() {
        val row: List<String> =
            mutableListOf(
                "1",
                "ASX",
                "SLB",
                "Test Asset",
                "split",
                TRADE_DATE,
                QUANTITY,
                PRICE,
                BROKERAGE,
                "AUD",
            )
        val trnAdapter = shareSightFactory.adapter(row)
        Assertions.assertThat(trnAdapter).isInstanceOf(
            ShareSightTradeAdapter::class.java,
        )
    }

    @Test
    fun is_RowWithNoCommentTransformed() {
        val row = getRow("buy", "0.8988", "2097.85").toMutableList()
        row[ShareSightTradeAdapter.COMMENTS] = "null"
        val trustedTrnImportRequest =
            TrustedTrnImportRequest(
                portfolio = getPortfolio("Test", NZD),
                importFormat = ImportFormat.SHARESIGHT,
                row = row,
            )
        val trn = shareSightRowProcessor.transform(trustedTrnImportRequest)
        Assertions.assertThat(trn)
            .hasFieldOrPropertyWithValue("TrnType", TrnType.BUY)
            .hasFieldOrPropertyWithValue("quantity", BigDecimal(10))
            .hasFieldOrPropertyWithValue("fees", BigDecimal(BROKERAGE)) // No FX Rate
            .hasFieldOrPropertyWithValue("price", BigDecimal(PRICE))
            .hasFieldOrPropertyWithValue("comments", null)
            .hasFieldOrProperty("assetId")
            .hasFieldOrProperty("tradeDate")
    }

    @Test
    fun is_SplitTransactionTransformed() {
        val row = getRow("split", "0", "null")
        val trn =
            shareSightRowProcessor.transform(
                TrustedTrnImportRequest(
                    portfolio = getPortfolio("Test", NZD),
                    importFormat = ImportFormat.SHARESIGHT,
                    row = row,
                ),
            )
        Assertions.assertThat(trn)
            .hasFieldOrPropertyWithValue("callerRef.callerId", "1")
            .hasFieldOrPropertyWithValue("TrnType", TrnType.SPLIT)
            .hasFieldOrPropertyWithValue("quantity", BigDecimal(QUANTITY))
            .hasFieldOrPropertyWithValue("price", BigDecimal(PRICE))
            .hasFieldOrPropertyWithValue("tradeAmount", BigDecimal.ZERO)
            .hasFieldOrPropertyWithValue("comments", "Test Comment")
            .hasFieldOrPropertyWithValue("tradeCurrency", AUD.code)
            .hasFieldOrProperty("assetId")
            .hasFieldOrProperty("tradeDate")
    }

    @Test
    fun is_IllegalDateHandled() {
        val row = getRow("buy", "0.8988", "2097.85").toMutableList()
        row.add(ShareSightTradeAdapter.DATE, "$TRADE_DATE'")
        assertThrows(
            BusinessException::class.java,
        ) {
            shareSightRowProcessor.transform(
                TrustedTrnImportRequest(
                    getPortfolio("Test", NZD),
                    importFormat = ImportFormat.SHARESIGHT,
                    row = row,
                ),
            )
        }
    }

    companion object {
        private const val PRICE = "12.23"

        fun getRow(
            tranType: String,
            fxRate: String,
            tradeAmount: String,
        ): List<String> {
            return getRow("AMP", "ASX", tranType, fxRate, tradeAmount)
        }

        private const val BROKERAGE = "12.99"

        private const val TRADE_DATE = "21/01/2019"

        private const val QUANTITY = "10"

        fun getRow(
            code: String,
            market: String,
            tranType: String,
            fxRate: String,
            tradeAmount: String,
        ): List<String> {
            val row: MutableList<String> = mutableListOf()
            row.add(ShareSightTradeAdapter.ID, "1")
            row.add(ShareSightTradeAdapter.MARKET, market)
            row.add(ShareSightTradeAdapter.CODE, code)
            row.add(ShareSightTradeAdapter.NAME, "Test Asset")
            row.add(ShareSightTradeAdapter.TYPE, tranType)
            row.add(ShareSightTradeAdapter.DATE, TRADE_DATE)
            row.add(ShareSightTradeAdapter.QUANTITY, QUANTITY)
            row.add(ShareSightTradeAdapter.PRICE, PRICE)
            row.add(ShareSightTradeAdapter.BROKERAGE, BROKERAGE)
            row.add(ShareSightTradeAdapter.CURRENCY, AUD.code)
            row.add(ShareSightTradeAdapter.FX_RATE, fxRate)
            row.add(ShareSightTradeAdapter.VALUE, tradeAmount)
            row.add(ShareSightTradeAdapter.COMMENTS, "Test Comment")
            return row
        }
    }
}
