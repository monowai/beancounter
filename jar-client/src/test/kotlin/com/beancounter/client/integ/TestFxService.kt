package com.beancounter.client.integ

import com.beancounter.client.FxService
import com.beancounter.client.config.ClientConfig
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import java.math.BigDecimal

@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:+:stubs:10999"]
)
@ImportAutoConfiguration(ClientConfig::class)
@SpringBootTest(classes = [ClientConfig::class])
/**
 * Client side fx rate functionality. Mocks out bc-data responses.
 */
class TestFxService {
    @Autowired
    private val fxRateService: FxService? = null

    @Autowired
    private val fxTransactions: FxTransactions? = null

    @Test
    fun is_FxContractHonoured() {
        val isoCurrencyPairs: ArrayList<IsoCurrencyPair> = ArrayList()
        isoCurrencyPairs.add(IsoCurrencyPair("USD", "EUR"))
        isoCurrencyPairs.add(IsoCurrencyPair("USD", "GBP"))
        isoCurrencyPairs.add(IsoCurrencyPair("USD", "NZD"))
        val testDate = "2019-11-12"
        val fxResponse = fxRateService!!.getRates(FxRequest(testDate, pairs = isoCurrencyPairs))
        assertThat(fxResponse).isNotNull.hasNoNullFieldsOrProperties()
        val fxPairResults: FxPairResults = fxResponse.data
        assertThat(fxPairResults.rates).isNotNull
        assertThat(fxPairResults.rates.size).isEqualTo(isoCurrencyPairs.size)

        for (isoCurrencyPair in isoCurrencyPairs) {

            assertThat(fxPairResults).isNotNull.hasFieldOrProperty("rates")
            assertThat(fxPairResults.rates).containsKeys(isoCurrencyPair)
            assertThat(fxPairResults.rates[isoCurrencyPair])
                .hasFieldOrPropertyWithValue("date", testDate)
        }
    }

    @Test
    fun is_EarlyDateWorking() {
        val isoCurrencyPairs: ArrayList<IsoCurrencyPair> = ArrayList()
        isoCurrencyPairs.add(IsoCurrencyPair("USD", "SGD"))
        isoCurrencyPairs.add(IsoCurrencyPair("GBP", "NZD"))
        val testDate = "1996-07-27" // Earlier than when ECB started recording rates
        val fxResponse = fxRateService!!.getRates(FxRequest(testDate, isoCurrencyPairs))
        assertThat(fxResponse)
            .isNotNull
            .hasNoNullFieldsOrProperties()
        val fxPairResults: FxPairResults = fxResponse.data

        for (isoCurrencyPair in isoCurrencyPairs) {
            assertThat(fxPairResults.rates).containsKeys(isoCurrencyPair)
            assertThat(fxPairResults.rates[isoCurrencyPair])
                .hasFieldOrPropertyWithValue("date", "1999-01-04")
        }
    }

    @Test
    fun is_EmptyResponseReturning() {
        val fxResponse = fxRateService!!.getRates(FxRequest("2020-10-01"))
        assertThat(fxResponse).isNotNull
        assertThat(fxResponse.data.rates).isEmpty()
    }

    @Test
    fun is_fxTransactionsSettingCorrectRates() {
        val trnInput = TrnInput(
            CallerRef(),
            AssetUtils.Companion.getAsset("NASDAQ", "MSFT").id,
            TrnType.BUY,
            quantity = BigDecimal.TEN,
            tradeDate = DateUtils().getDate("2019-07-26"),
            price = BigDecimal.TEN
        )
        trnInput.cashCurrency = "USD"
        val portfolio = getPortfolio("ABC")
        val request = fxTransactions!!.buildRequest(portfolio, trnInput)
        assertThat(request).hasFieldOrProperty("tradePf")
        fxTransactions.setTrnRates(portfolio, trnInput)
        assertThat(trnInput)
            .isNotNull
            .hasFieldOrPropertyWithValue("tradeCashRate", BigDecimal.ONE)
            .hasFieldOrPropertyWithValue("tradeBaseRate", BigDecimal.ONE)
            .hasFieldOrPropertyWithValue("tradePortfolioRate", BigDecimal("0.66428103"))
    }

    @Test
    fun is_NoArgsWorking() {
        var response = fxRateService!!.getRates(FxRequest("2020-01-10"))
        assertThat(response)
            .isNotNull
            .hasFieldOrProperty("data")
        assertThat(response.data.rates).isNotNull.isEmpty()
        response = fxRateService.getRates(FxRequest(rateDate = "2020-01-01"))
        assertThat(response)
            .isNotNull
            .hasFieldOrProperty("data")
        assertThat(response.data.rates).isNotNull.isEmpty()
    }
}
