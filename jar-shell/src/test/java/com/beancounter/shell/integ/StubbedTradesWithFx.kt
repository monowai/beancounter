package com.beancounter.shell.integ

import com.beancounter.client.config.ClientConfig
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.client.sharesight.ShareSightConfig
import com.beancounter.client.sharesight.ShareSightFactory
import com.beancounter.common.input.ImportFormat
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.CurrencyUtils
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import lombok.extern.slf4j.Slf4j
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal

@Tag("slow")
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:+:stubs:10999"]
)
@ActiveProfiles("test")
@Slf4j
@SpringBootTest(classes = [ShareSightConfig::class, ClientConfig::class])
internal class StubbedTradesWithFx {
    private val currencyUtils = CurrencyUtils()

    @Autowired
    private lateinit var fxTransactions: FxTransactions

    @Autowired
    private lateinit var shareSightFactory: ShareSightFactory

    @Autowired
    private lateinit var shareSightConfig: ShareSightConfig

    @Test
    fun is_FxRatesSetFromCurrencies() {
        val testDate = "27/07/2019" // Sharesight format

        val row: List<String> = arrayListOf(
            "999",
            "LSE",
            "BHP",
            "Test Asset",
            "buy",
            testDate,
            "10",
            "12.23",
            "12.99",
            "GBP",
            BigDecimal.ZERO.toString(),
            "2097.85"
        )

        val trades = shareSightFactory.adapter(row)

        // Portfolio is in NZD
        val portfolio = getPortfolio("TEST", currencyUtils.getCurrency("NZD"))
        val trustedTrnImportRequest = TrustedTrnImportRequest(
            portfolio,
            row, ImportFormat.SHARESIGHT
        )
        val trn = trades.from(trustedTrnImportRequest)
        Assertions.assertThat(trn).isNotNull
        fxTransactions.setTrnRates(portfolio, trn)
        assertTransaction(portfolio, trn)
    }

    @Test
    fun is_FxRateOverridenFromSourceData() {
        // NZD Portfolio
        // USD System Base
        // GBP Trade
        Assertions.assertThat(shareSightConfig.isCalculateRates).isTrue
        val row: List<String> = arrayListOf(
            "999",
            "LSE",
            "BHP",
            "Test Asset",
            "buy",
            "27/07/2019",
            "10",
            "12.23",
            "12.99",
            "GBP",
            "99.99",
            "2097.85"
        )
        // With switch true, ignore the supplied rate and pull from service
        val trades = shareSightFactory.adapter(row)

        // Portfolio is in NZD
        val portfolio = getPortfolio("Test", currencyUtils.getCurrency("NZD"))
        val trustedTrnImportRequest = TrustedTrnImportRequest(
            portfolio,
            row, ImportFormat.SHARESIGHT
        )
        val trn = trades.from(trustedTrnImportRequest)
        Assertions.assertThat(trn).isNotNull
        fxTransactions.setTrnRates(portfolio, trn)
        assertTransaction(portfolio, trn)
    }

    @Test
    fun is_FxRatesSetAndTradeAmountCalculated() {

        // Trade CCY USD
        val row: List<String> = arrayListOf(
            "333",
            "NASDAQ",
            "MSFT",
            "MSFT",
            "BUY",
            "18/10/2019",
            "10",
            "100",
            BigDecimal.ZERO.toString(),
            "USD",
            BigDecimal.ZERO.toString(),
            "1001.00"
        )
        val trades = shareSightFactory.adapter(row)

        // Testing all currency buckets
        val portfolio = Portfolio(
            "Test",
            Currency("NZD"),
            Currency("GBP")
        )
        val trustedTrnImportRequest = TrustedTrnImportRequest(
            portfolio,
            row, ImportFormat.SHARESIGHT
        )
        val trn = trades.from(trustedTrnImportRequest)
        Assertions.assertThat(trn).isNotNull
        fxTransactions.setTrnRates(portfolio, trn)
        Assertions.assertThat(trn)
            .hasFieldOrPropertyWithValue("tradeCurrency", "USD") // Was tradeAmount calculated?
            .hasFieldOrPropertyWithValue("tradeAmount", BigDecimal("1000.00"))
            .hasFieldOrPropertyWithValue("tradeBaseRate", BigDecimal("1.28929253"))
            .hasFieldOrPropertyWithValue("tradeCashRate", BigDecimal("0.63723696"))
            .hasFieldOrPropertyWithValue("tradePortfolioRate", BigDecimal("0.63723696"))
    }

    @Test
    fun is_RateOfOneSetForUndefinedCurrencies() {
        val testDate = "27/07/2019"

        // Trade CCY USD
        val row: List<String> = arrayListOf(
            "222",
            "NASDAQ",
            "MSFT",
            "MSFT",
            "BUY",
            testDate,
            "10",
            "100",
            BigDecimal.ZERO.toString(),
            "USD",
            BigDecimal.ONE.toString(),
            "1000.00"
        )
        val trades = shareSightFactory.adapter(row)

        // Testing all currency buckets
        val portfolio = getPortfolio("TEST", currencyUtils.getCurrency("USD"))
        val trustedTrnImportRequest = TrustedTrnImportRequest(
            portfolio,
            row, ImportFormat.SHARESIGHT
        )
        val trn = trades.from(trustedTrnImportRequest)
        Assertions.assertThat(trn).isNotNull
        trn.cashCurrency = null
        fxTransactions.setTrnRates(portfolio, trn)

        // No currencies are defined so rate defaults to 1
        Assertions.assertThat(trn)
            .hasFieldOrPropertyWithValue("tradeCurrency", "USD")
            .hasFieldOrPropertyWithValue("tradeBaseRate", BigDecimal.ONE)
            .hasFieldOrPropertyWithValue("tradeCashRate", BigDecimal.ONE)
            .hasFieldOrPropertyWithValue("tradePortfolioRate", BigDecimal.ONE)
    }

    private fun assertTransaction(portfolio: Portfolio, trn: TrnInput?) {
        Assertions.assertThat(trn)
            .hasFieldOrPropertyWithValue("trnType", TrnType.BUY)
            .hasFieldOrPropertyWithValue("tradeCurrency", "GBP")
            .hasFieldOrPropertyWithValue("cashCurrency", portfolio.currency.code)
            .hasFieldOrPropertyWithValue("tradeBaseRate", BigDecimal("0.80474951"))
            .hasFieldOrPropertyWithValue("tradeCashRate", BigDecimal("0.53457983"))
            .hasFieldOrPropertyWithValue("tradeCashRate", BigDecimal("0.53457983"))
            .hasFieldOrProperty("tradeDate")
    }
}
