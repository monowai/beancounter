package com.beancounter.client.integ

import com.beancounter.client.config.ClientConfig
import com.beancounter.client.sharesight.ShareSightConfig
import com.beancounter.client.sharesight.ShareSightFactory
import com.beancounter.client.sharesight.ShareSightRowAdapter
import com.beancounter.client.sharesight.ShareSightTradeAdapter
import com.beancounter.common.input.ImportFormat
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.Asset
import com.beancounter.common.model.CallerRef
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.test.context.ActiveProfiles
import java.util.ArrayList

/**
 * Test conversion from sharesight format to internal format.
 */
@ActiveProfiles("test")
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:+:stubs:10999"]
)
@SpringBootTest(classes = [ShareSightConfig::class, ClientConfig::class])
internal class ShareSightAdapterTest {
    @Autowired
    private lateinit var shareSightFactory: ShareSightFactory

    @Autowired
    private lateinit var shareSightConfig: ShareSightConfig

    @Autowired
    private lateinit var shareSightRowProcessor: ShareSightRowAdapter

    @Test
    fun is_ExchangeAliasReturnedInAssetCode() {
        var expectedAsset = getAsset("NYSE", "ABBV")
        verifyMarketCode("ABBV.NYSE", expectedAsset)
        expectedAsset = getAsset("ASX", "AMP")
        verifyMarketCode("AMP.AX", expectedAsset)
    }

    @Test
    fun is_IgnoreRatesCorrect() {
        Assertions.assertThat(shareSightConfig.isCalculateRates).isTrue
    }

    private fun verifyMarketCode(code: String, expectedAsset: Asset) {
        val row: MutableList<String> = ArrayList()
        row.add("1")
        row.add(code)
        val asset = shareSightFactory.shareSightDivi.resolveAsset(row)
        Assertions.assertThat(asset!!.market.code)
            .isEqualTo(expectedAsset.market.code)
    }

    @Test
    fun is_AssetsSetIntoTransaction() {
        var row: MutableList<String> = mutableListOf()
        row.add(ShareSightTradeAdapter.id, "1")
        row.add(ShareSightTradeAdapter.market, "ASX")
        row.add(ShareSightTradeAdapter.code, "BHP")
        row.add(ShareSightTradeAdapter.name, "Test Asset")
        row.add(ShareSightTradeAdapter.type, "buy")
        row.add(ShareSightTradeAdapter.date, "21/01/2019")
        row.add(ShareSightTradeAdapter.quantity, "10")
        row.add(ShareSightTradeAdapter.price, "12.23")
        row.add(ShareSightTradeAdapter.brokerage, "12.99")
        row.add(ShareSightTradeAdapter.currency, "AUD")
        row.add(ShareSightTradeAdapter.fxRate, "99.99")
        row.add(ShareSightTradeAdapter.value, "2097.85")
        val rows: MutableList<List<String>> = ArrayList()
        rows.add(row)
        row = ArrayList()
        row.add(ShareSightTradeAdapter.id, "2")
        row.add(ShareSightTradeAdapter.market, "NASDAQ")
        row.add(ShareSightTradeAdapter.code, "MSFT")
        row.add(ShareSightTradeAdapter.name, "Microsoft")
        row.add(ShareSightTradeAdapter.type, "buy")
        row.add(ShareSightTradeAdapter.date, "21/01/2019")
        row.add(ShareSightTradeAdapter.quantity, "10")
        row.add(ShareSightTradeAdapter.price, "12.23")
        row.add(ShareSightTradeAdapter.brokerage, "12.99")
        row.add(ShareSightTradeAdapter.currency, "USD")
        row.add(ShareSightTradeAdapter.fxRate, "99.99")
        row.add(ShareSightTradeAdapter.value, "2097.85")
        rows.add(row)
        val trnInputs: MutableCollection<TrnInput> = ArrayList()
        val portfolio = getPortfolio("TEST")
        for (columnValues in rows) {
            val trustedTrnImportRequest = TrustedTrnImportRequest(
                portfolio, ImportFormat.SHARESIGHT,
                CallerRef(),
                "",
                columnValues
            )
            trnInputs.add(
                shareSightRowProcessor
                    .transform(trustedTrnImportRequest)
            )
        }
        Assertions.assertThat(trnInputs).hasSize(2)
        for (trn in trnInputs) {
            Assertions.assertThat(trn)
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
