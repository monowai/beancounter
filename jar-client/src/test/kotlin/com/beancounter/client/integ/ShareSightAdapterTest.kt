package com.beancounter.client.integ

import com.beancounter.auth.TokenService
import com.beancounter.client.Constants.Companion.ASX
import com.beancounter.client.Constants.Companion.NYSE
import com.beancounter.client.config.ClientConfig
import com.beancounter.client.sharesight.ShareSightConfig
import com.beancounter.client.sharesight.ShareSightFactory
import com.beancounter.client.sharesight.ShareSightRowAdapter
import com.beancounter.client.sharesight.ShareSightTradeAdapter
import com.beancounter.common.input.ImportFormat
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.Asset
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Test conversion from sharesight format to internal format.
 */
@ActiveProfiles("jar-client-shared", "contract-base")
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:0.1.1:stubs:10990"]
)
@SpringBootTest(classes = [ShareSightConfig::class, ClientConfig::class])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
internal class ShareSightAdapterTest {
    @Autowired
    private lateinit var shareSightFactory: ShareSightFactory

    @Autowired
    private lateinit var shareSightConfig: ShareSightConfig

    @Autowired
    private lateinit var shareSightRowProcessor: ShareSightRowAdapter

    @MockitoBean
    lateinit var tokenService: TokenService

    @Test
    fun `should return exchange alias in asset code`() {
        var expectedAsset =
            getTestAsset(
                NYSE,
                "ABBV"
            )
        verifyMarketCode(
            "ABBV.NYSE",
            expectedAsset
        )
        expectedAsset =
            getTestAsset(
                ASX,
                "AMP"
            )
        verifyMarketCode(
            "AMP.AX",
            expectedAsset
        )
    }

    @Test
    fun `should ignore rates correctly`() {
        assertThat(shareSightConfig.isCalculateRates).isTrue
    }

    private fun verifyMarketCode(
        code: String,
        expectedAsset: Asset
    ) {
        val row: MutableList<String> = ArrayList()
        row.add("1")
        row.add(code)
        val asset = shareSightFactory.shareSightDivi.resolveAsset(row)
        assertThat(asset.market.code)
            .isEqualTo(expectedAsset.market.code)
    }

    @Test
    fun `should set assets into transaction`() {
        val rows =
            listOf(
                buildTradeRow("1", ASX.code, "BHP", "Test Asset", "AUD"),
                buildTradeRow("2", "NASDAQ", "MSFT", "Microsoft", "USD")
            )
        val trnInputs = transformRows(rows)
        assertThat(trnInputs).hasSize(2)
        for (trn in trnInputs) {
            assertThat(trn)
                .hasFieldOrProperty("callerRef")
                .hasFieldOrProperty("assetId")
                .hasFieldOrProperty("fees")
                .hasFieldOrProperty("quantity")
                .hasFieldOrProperty("tradeCurrency")
                .hasFieldOrProperty("trnType")
                .hasFieldOrProperty("tradeDate")
        }
    }

    private fun buildTradeRow(
        id: String,
        market: String,
        code: String,
        name: String,
        currency: String
    ): List<String> {
        val row: MutableList<String> = mutableListOf()
        row.add(ShareSightTradeAdapter.ID, id)
        row.add(ShareSightTradeAdapter.MARKET, market)
        row.add(ShareSightTradeAdapter.CODE, code)
        row.add(ShareSightTradeAdapter.NAME, name)
        row.add(ShareSightTradeAdapter.TYPE, "buy")
        row.add(ShareSightTradeAdapter.DATE, "21/01/2019")
        row.add(ShareSightTradeAdapter.QUANTITY, "10")
        row.add(ShareSightTradeAdapter.PRICE, "12.23")
        row.add(ShareSightTradeAdapter.BROKERAGE, "12.99")
        row.add(ShareSightTradeAdapter.CURRENCY, currency)
        row.add(ShareSightTradeAdapter.FX_RATE, "99.99")
        row.add(ShareSightTradeAdapter.VALUE, "2097.85")
        return row
    }

    private fun transformRows(rows: List<List<String>>): List<TrnInput> {
        val portfolio = getPortfolio()
        return rows.map { columnValues ->
            shareSightRowProcessor.transform(
                TrustedTrnImportRequest(
                    portfolio,
                    ImportFormat.SHARESIGHT,
                    row = columnValues
                )
            )
        }
    }
}