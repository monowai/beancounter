package com.beancounter.client.integ

import com.beancounter.client.Constants.Companion.NZD
import com.beancounter.client.Constants.Companion.USD
import com.beancounter.client.config.ClientConfig
import com.beancounter.client.integ.ShareSightTradeTest.Companion.getRow
import com.beancounter.client.sharesight.ShareSightConfig
import com.beancounter.client.sharesight.ShareSightDividendAdapter
import com.beancounter.client.sharesight.ShareSightFactory
import com.beancounter.client.sharesight.ShareSightRowAdapter
import com.beancounter.common.input.ImportFormat
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.MathUtils.Companion.multiplyAbs
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal

@ActiveProfiles("infile")
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:+:stubs:10999"]
)
@SpringBootTest(classes = [ShareSightConfig::class, ClientConfig::class])
/**
 * FX rates set in incoming csv data are preserved.
 */
class ShareSightRatesInTrn {

    @Autowired
    private lateinit var shareSightFactory: ShareSightFactory

    @Autowired
    private lateinit var shareSightConfig: ShareSightConfig

    @Autowired
    private lateinit var shareSightRowProcessor: ShareSightRowAdapter

    @BeforeEach
    fun is_IgnoreRatesDefaultCorrect() {
        // Assumptions for all tests in this class
        Assertions.assertThat(shareSightConfig.isCalculateRates).isFalse
        Assertions.assertThat(shareSightConfig.isCalculateAmount).isFalse
    }

    private val testComment = "Test Comment"

    @Test
    fun is_DividendRowWithFxConverted() {
        val row: MutableList<String> = mutableListOf()

        // Portfolio is in NZD
        val portfolio = getPortfolio()
        Assertions.assertThat(portfolio).isNotNull

        // Trade is in USD
        row.add(ShareSightDividendAdapter.id, "ABC")
        row.add(ShareSightDividendAdapter.code, "ABBV.NYS")
        row.add(ShareSightDividendAdapter.name, "Test Asset")
        row.add(ShareSightDividendAdapter.date, "21/01/2019")
        val rate = "0.8074" // Sharesight Trade to Reference Rate
        row.add(ShareSightDividendAdapter.fxRate, rate)
        row.add(ShareSightDividendAdapter.currency, USD.code) // TradeCurrency
        val net = "15.85"
        row.add(ShareSightDividendAdapter.net, net)
        row.add(ShareSightDividendAdapter.tax, BigDecimal.ZERO.toString())
        row.add(ShareSightDividendAdapter.gross, net)
        row.add(ShareSightDividendAdapter.comments, testComment)
        val dividends = shareSightFactory.adapter(row)
        val trustedTrnImportRequest = TrustedTrnImportRequest(
            portfolio,
            row, ImportFormat.SHARESIGHT
        )
        val trn = dividends.from(trustedTrnImportRequest)
        val fxRate = BigDecimal(rate)
        Assertions.assertThat(trn) // Id comes from svc-data/contracts/assets
            .hasFieldOrPropertyWithValue("callerRef.callerId", "ABC")
            .hasFieldOrPropertyWithValue("assetId", "BguoVZpoRxWeWrITp7DEuw")
            .hasFieldOrPropertyWithValue("tradeCashRate", fxRate)
            .hasFieldOrPropertyWithValue(
                "tradeAmount",
                multiplyAbs(BigDecimal(net), fxRate)
            )
            .hasFieldOrPropertyWithValue(
                "cashAmount",
                multiplyAbs(BigDecimal(net), fxRate)
            )
            .hasFieldOrPropertyWithValue("tax", BigDecimal.ZERO)
            .hasFieldOrPropertyWithValue("comments", row[ShareSightDividendAdapter.comments])
            .hasFieldOrPropertyWithValue("tradeCurrency", USD.code)
            .hasFieldOrProperty("tradeDate")
    }

    @Test
    @Throws(Exception::class)
    fun is_TradeRowWithFxConverted() {
        val fxRate = "0.8988"
        val tradeAmount = "2097.85"
        val row: List<String> = getRow("buy", fxRate, tradeAmount)
        // Portfolio is in NZD
        val portfolio = getPortfolio("is_TradeRowWithFxConverted", NZD)
        // System base currency
        val trustedTrnImportRequest = TrustedTrnImportRequest(
            portfolio,
            row, ImportFormat.SHARESIGHT
        )
        val trn = shareSightRowProcessor.transform(trustedTrnImportRequest)

        log.info(BcJson().objectMapper.writeValueAsString(trn))
        Assertions.assertThat(trn)
            .hasFieldOrPropertyWithValue("trnType", TrnType.BUY)
            .hasFieldOrPropertyWithValue("quantity", BigDecimal(10))
            .hasFieldOrPropertyWithValue("price", BigDecimal("12.23"))
            .hasFieldOrPropertyWithValue("fees", BigDecimal("14.45"))
            .hasFieldOrPropertyWithValue(
                "tradeAmount",
                multiplyAbs(BigDecimal(tradeAmount), BigDecimal(fxRate))
            )
            .hasFieldOrPropertyWithValue("comments", testComment)
            .hasFieldOrProperty("tradeCurrency")
            .hasFieldOrPropertyWithValue("tradeCashRate", BigDecimal(fxRate))
            .hasFieldOrProperty("tradeDate")
    }

    companion object {
        private val log = LoggerFactory.getLogger(ShareSightRatesInTrn::class.java)
    }
}
