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
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.test.context.ActiveProfiles

/**
 * Test conversion from sharesight format to internal format.
 */
@ActiveProfiles("test")
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:+:stubs:10999"],
)
@SpringBootTest(classes = [ShareSightConfig::class, ClientConfig::class])
internal class ShareSightAdapterTest {
    @Autowired
    private lateinit var shareSightFactory: ShareSightFactory

    @Autowired
    private lateinit var shareSightConfig: ShareSightConfig

    @Autowired
    private lateinit var shareSightRowProcessor: ShareSightRowAdapter

    @MockBean
    lateinit var tokenService: TokenService

    @Test
    fun is_ExchangeAliasReturnedInAssetCode() {
        var expectedAsset = getTestAsset(NYSE, "ABBV")
        verifyMarketCode("ABBV.NYSE", expectedAsset)
        expectedAsset = getTestAsset(ASX, "AMP")
        verifyMarketCode("AMP.AX", expectedAsset)
    }

    @Test
    fun is_IgnoreRatesCorrect() {
        assertThat(shareSightConfig.isCalculateRates).isTrue
    }

    private fun verifyMarketCode(
        code: String,
        expectedAsset: Asset,
    ) {
        val row: MutableList<String> = ArrayList()
        row.add("1")
        row.add(code)
        val asset = shareSightFactory.shareSightDivi.resolveAsset(row)
        assertThat(asset.market.code)
            .isEqualTo(expectedAsset.market.code)
    }

    @Test
    fun is_AssetsSetIntoTransaction() {
        var row: MutableList<String> = mutableListOf()
        row.add(ShareSightTradeAdapter.ID, "1")
        row.add(ShareSightTradeAdapter.MARKET, ASX.code)
        row.add(ShareSightTradeAdapter.CODE, "BHP")
        row.add(ShareSightTradeAdapter.NAME, "Test Asset")
        row.add(ShareSightTradeAdapter.TYPE, "buy")
        row.add(ShareSightTradeAdapter.DATE, "21/01/2019")
        row.add(ShareSightTradeAdapter.QUANTITY, "10")
        row.add(ShareSightTradeAdapter.PRICE, "12.23")
        row.add(ShareSightTradeAdapter.BROKERAGE, "12.99")
        row.add(ShareSightTradeAdapter.CURRENCY, "AUD")
        row.add(ShareSightTradeAdapter.FX_RATE, "99.99")
        row.add(ShareSightTradeAdapter.VALUE, "2097.85")
        val rows: MutableList<List<String>> = ArrayList()
        rows.add(row)
        row = ArrayList()
        row.add(ShareSightTradeAdapter.ID, "2")
        row.add(ShareSightTradeAdapter.MARKET, "NASDAQ")
        row.add(ShareSightTradeAdapter.CODE, "MSFT")
        row.add(ShareSightTradeAdapter.NAME, "Microsoft")
        row.add(ShareSightTradeAdapter.TYPE, "buy")
        row.add(ShareSightTradeAdapter.DATE, "21/01/2019")
        row.add(ShareSightTradeAdapter.QUANTITY, "10")
        row.add(ShareSightTradeAdapter.PRICE, "12.23")
        row.add(ShareSightTradeAdapter.BROKERAGE, "12.99")
        row.add(ShareSightTradeAdapter.CURRENCY, "USD")
        row.add(ShareSightTradeAdapter.FX_RATE, "99.99")
        row.add(ShareSightTradeAdapter.VALUE, "2097.85")
        rows.add(row)
        val trnInputs: MutableCollection<TrnInput> = ArrayList()
        val portfolio = getPortfolio()
        for (columnValues in rows) {
            val trustedTrnImportRequest =
                TrustedTrnImportRequest(
                    portfolio,
                    ImportFormat.SHARESIGHT,
                    row = columnValues,
                )
            trnInputs.add(
                shareSightRowProcessor
                    .transform(trustedTrnImportRequest),
            )
        }
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
}
