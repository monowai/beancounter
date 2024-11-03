package com.beancounter.client.integ

import com.beancounter.auth.AutoConfigureNoAuth
import com.beancounter.auth.TokenService
import com.beancounter.client.Constants.Companion.NZD
import com.beancounter.client.Constants.Companion.USD
import com.beancounter.client.FxService
import com.beancounter.client.config.ClientConfig
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.utils.PortfolioUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import java.math.BigDecimal

/**
 * Verify how the fx rates are set into a transaction.
 */
@ImportAutoConfiguration(ClientConfig::class)
@SpringBootTest(classes = [ClientConfig::class])
@AutoConfigureNoAuth
class FxTransactionsRatesTest {
    @Autowired
    private lateinit var fxTransactions: FxTransactions

    @MockBean
    private lateinit var fxClientService: FxService

    @MockBean
    lateinit var tokenService: TokenService

    @Test
    fun ratesAreNeededForTrnInput() {
        assertThat(fxTransactions.needsRates(TrnInput())).isTrue()
        assertThat(fxTransactions.needsRates(TrnInput(tradePortfolioRate = BigDecimal.ONE)))
            .isTrue()
        assertThat(fxTransactions.needsRates(TrnInput(tradeCashRate = BigDecimal.ONE)))
            .isTrue()
        assertThat(fxTransactions.needsRates(TrnInput(tradeBaseRate = BigDecimal.ONE)))
            .isTrue()
    }

    @Test
    fun ratesNotNeededWhenSet() {
        assertThat(
            fxTransactions.needsRates(
                TrnInput(
                    tradePortfolioRate = BigDecimal.ONE,
                    tradeCashRate = BigDecimal.ONE,
                    tradeBaseRate = BigDecimal.ONE,
                ),
            ),
        ).isFalse()
    }

    @Test
    fun ratesAreSet() {
        val portfolio = PortfolioUtils.getPortfolio()
        val trnInput = TrnInput()
        val fxRequest = fxTransactions.getFxRequest(portfolio, trnInput)
        val rate = BigDecimal("1.50")
        val fxPairResults =
            FxPairResults(
                mapOf(Pair(IsoCurrencyPair(USD.code, NZD.code), FxRate(from = USD, to = NZD, rate = rate))),
            )
        Mockito
            .`when`(fxClientService.getRates(fxRequest))
            .thenReturn(
                FxResponse(
                    data = fxPairResults,
                ),
            )

        Mockito.`when`(tokenService.bearerToken).thenReturn("")
        fxTransactions.setRates(portfolio, trnInput)
        assertThat(trnInput)
            .hasFieldOrPropertyWithValue("tradeCurrency", USD.code)
            .hasFieldOrPropertyWithValue("tradeCashRate", BigDecimal.ONE)
            .hasFieldOrPropertyWithValue("tradeBaseRate", BigDecimal.ONE)
            .hasFieldOrPropertyWithValue("tradePortfolioRate", rate)
    }
}
