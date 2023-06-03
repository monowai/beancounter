package com.beancounter.shell.integ

import com.beancounter.auth.TokenService
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
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.shell.Constants.Companion.GBP
import com.beancounter.shell.Constants.Companion.NZD
import com.beancounter.shell.Constants.Companion.USD
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal

private const val LON = "LON"

/**
 * Trade tests with FX rates obtained from bc-data.
 */

@Tag("slow")
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:+:stubs:10999"],
)
@ActiveProfiles("test")
@SpringBootTest(classes = [ShareSightConfig::class, ClientConfig::class])
internal class StubbedTradesWithFx {
    @Autowired
    private lateinit var fxTransactions: FxTransactions

    @Autowired
    private lateinit var shareSightFactory: ShareSightFactory

    @Autowired
    private lateinit var shareSightConfig: ShareSightConfig
    private val testDate = "27/07/2019" // Sharesight format

    @MockBean
    private lateinit var tokenService: TokenService

    @Test
    fun fxRatesSetFromCurrencies() {
        val row: List<String> = arrayListOf(
            "999",
            LON,
            "BHP",
            "Test Asset",
            "buy",
            testDate,
            "10",
            "12.23",
            "12.99",
            "GBP",
            BigDecimal.ZERO.toString(),
            "2097.85",
        )

        val trades = shareSightFactory.adapter(row)

        // Portfolio is in NZD
        val portfolio = getPortfolio("NZDTest", Currency("NZD"))
        val trustedTrnImportRequest = TrustedTrnImportRequest(
            portfolio,
            importFormat = ImportFormat.SHARESIGHT,
            row = row,
        )
        val trn = trades.from(trustedTrnImportRequest)
        assertThat(trn).isNotNull
        fxTransactions.setRates(portfolio, trn)
        assertTransaction(portfolio, trn)
    }

    @Test
    fun fxRateOverridesSourceData() {
        // NZD Portfolio
        // USD System Base
        // GBP Trade
        assertThat(shareSightConfig.isCalculateRates).isTrue
        val row: List<String> = arrayListOf(
            "999",
            LON,
            "BHP",
            "Test Asset",
            "buy",
            testDate,
            "10",
            "12.23",
            "12.99",
            GBP.code,
            "99.99",
            "2097.85",
        )
        // With switch true, ignore the supplied rate and pull from service
        val trades = shareSightFactory.adapter(row)

        // Portfolio is in NZD
        val portfolio = getPortfolio("Test", NZD)
        val trustedTrnImportRequest = TrustedTrnImportRequest(
            portfolio,
            importFormat = ImportFormat.SHARESIGHT,
            row = row,
        )
        val trn = trades.from(trustedTrnImportRequest)
        assertThat(trn).isNotNull
        fxTransactions.setRates(portfolio, trn)
        assertTransaction(portfolio, trn)
    }

    private val tradeBaseRateProp = "tradeBaseRate"
    private val tradeCashRateProp = "tradeCashRate"
    private val tradeAmountProp = "tradeAmount"
    private val tradePortfolioRateProp = "tradePortfolioRate"
    private val tradeCurrencyProp = "tradeCurrency"

    private val msft = "MSFT"

    @Test
    fun fxRatesSetAndTradeAmountCalculated() {
        // Trade CCY USD
        val row: List<String> = arrayListOf(
            "333",
            "NASDAQ",
            msft,
            msft,
            "BUY",
            "18/10/2019",
            "10",
            "100",
            BigDecimal.ZERO.toString(),
            USD.code,
            BigDecimal.ZERO.toString(),
            "1001.00",
        )
        val trades = shareSightFactory.adapter(row)

        // Testing all currency buckets
        val portfolio = Portfolio(
            id = "Test",
            code = "Test",
            currency = NZD,
            base = GBP,
        )
        val trustedTrnImportRequest = TrustedTrnImportRequest(
            portfolio,
            importFormat = ImportFormat.SHARESIGHT,
            row = row,
        )
        val trn = trades.from(trustedTrnImportRequest)
        assertThat(trn).isNotNull
        fxTransactions.setRates(portfolio, trn)
        val fxRate = "1.5692749462"
        assertThat(trn)
            .hasFieldOrPropertyWithValue(tradeCurrencyProp, USD.code) // Was tradeAmount calculated?
            .hasFieldOrPropertyWithValue(tradeAmountProp, BigDecimal("1000.00"))
            .hasFieldOrPropertyWithValue(tradeBaseRateProp, BigDecimal("0.7756191673"))
            .hasFieldOrPropertyWithValue(tradeCashRateProp, BigDecimal(fxRate))
            .hasFieldOrPropertyWithValue(tradePortfolioRateProp, BigDecimal(fxRate))
    }

    @Test
    fun rateOfOneSetForUndefinedCurrencies() {
        // Trade CCY USD
        val row: List<String> = arrayListOf(
            "222",
            "NASDAQ",
            msft,
            msft,
            "BUY",
            testDate,
            "10",
            "100",
            BigDecimal.ZERO.toString(),
            "USD",
            BigDecimal.ONE.toString(),
            "1000.00",
        )
        val trades = shareSightFactory.adapter(row)

        // Testing all currency buckets
        val portfolio = getPortfolio("TEST", USD)
        val trustedTrnImportRequest = TrustedTrnImportRequest(
            portfolio,
            importFormat = ImportFormat.SHARESIGHT,
            row = row,
        )
        val trn = trades.from(trustedTrnImportRequest)
        assertThat(trn).isNotNull
        fxTransactions.setRates(portfolio, trn)

        // No currencies are defined so rate defaults to 1
        assertThat(trn)
            .hasFieldOrPropertyWithValue(tradeCurrencyProp, USD.code)
            .hasFieldOrPropertyWithValue(tradeBaseRateProp, BigDecimal.ONE)
            .hasFieldOrPropertyWithValue(tradeCashRateProp, BigDecimal.ONE)
            .hasFieldOrPropertyWithValue(tradePortfolioRateProp, BigDecimal.ONE)
    }

    private fun assertTransaction(portfolio: Portfolio, trn: TrnInput?) {
        assertThat(trn)
            .hasFieldOrPropertyWithValue("trnType", TrnType.BUY)
            .hasFieldOrPropertyWithValue(tradeCurrencyProp, GBP.code)
            .hasFieldOrPropertyWithValue("cashCurrency", portfolio.currency.code)
            .hasFieldOrPropertyWithValue(tradeBaseRateProp, BigDecimal("1.24262269"))
            .hasFieldOrPropertyWithValue(tradeCashRateProp, BigDecimal("1.87062801"))
            .hasFieldOrPropertyWithValue(tradeCashRateProp, BigDecimal("1.87062801"))
            .hasFieldOrProperty("tradeDate")
    }
}
