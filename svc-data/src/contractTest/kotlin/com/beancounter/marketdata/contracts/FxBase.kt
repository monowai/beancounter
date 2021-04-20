package com.beancounter.marketdata.contracts

import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.providers.fxrates.EcbRates
import com.beancounter.marketdata.providers.fxrates.FxGateway
import com.beancounter.marketdata.utils.EcbMockUtils
import io.restassured.module.mockmvc.RestAssuredMockMvc
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal

/**
 * Base class for FX Contract tests
 */
class FxBase : ContractVerifierBase() {
    @MockBean
    private lateinit var fxGateway: FxGateway

    @Throws(Exception::class)
    @BeforeEach
    private fun mockAssets() {
        val mockMvc = MockMvcBuilders
            .webAppContextSetup(context)
            .build()
        RestAssuredMockMvc.mockMvc(mockMvc)
        val eur = "0.8973438622"
        val sgd = "1.3652189519"
        val gbp = "0.7756191673"
        val nzd = "1.5692749462"
        val aud = "1.4606963388"
        var rates: Map<String, BigDecimal> = EcbMockUtils.getRateMap(
            eur, sgd, gbp, nzd, aud
        )
        mockEcbRates(rates, EcbMockUtils.get("2019-10-20", rates))
        rates = EcbMockUtils.getRateMap(
            eur, sgd, gbp, "10.0", aud
        )
        mockEcbRates(rates, EcbMockUtils.get("2019-01-01", rates))
        rates = EcbMockUtils.getRateMap(
            eur, sgd, gbp, nzd, aud
        )
        mockEcbRates(rates, EcbMockUtils.get(rateDate, rates))

        // Current
        mockEcbRates(rates, EcbMockUtils.get(dateUtils.today(), rates))
        rates = EcbMockUtils.getRateMap(
            "0.897827258", "1.3684683067", "0.8047495062",
            "1.5053869635", "1.4438857964"
        )
        mockEcbRates(rates, EcbMockUtils.get("2019-07-26", rates))
        // Saturday results are the same as Fridays
        mockEcbRates(rates, EcbMockUtils.get("2019-07-26", rates), "2019-07-27")
        rates = EcbMockUtils.getRateMap(
            "0.9028530155", "1.3864210906", "0.8218941856",
            "1.5536294691", "1.4734561213"
        )
        mockEcbRates(rates, EcbMockUtils.get("2019-08-16", rates))
        rates = EcbMockUtils.getRateMap(
            "0.9078529278", "1.36123468", "0.7791193827",
            "1.5780299591", "1.460463005"
        )
        mockEcbRates(rates, EcbMockUtils.get("2019-11-12", rates))
        rates = EcbMockUtils.getRateMap(
            "0.8482483671", "1.6586648571", "0.6031894139",
            "1.8855712953", "1.6201543812"
        )
        mockEcbRates(rates, EcbMockUtils.get("1999-01-04", rates))
    }

    private fun mockEcbRates(
        rates: Map<String, BigDecimal>,
        ecbRates: EcbRates,
        rateDate: String = dateUtils.getDateString(ecbRates.date)
    ) {
        Mockito.`when`(
            fxGateway.getRatesForSymbols(
                rateDate, Constants.USD.code,
                java.lang.String.join(",", rates.keys)
            )
        )
            .thenReturn(ecbRates)
    }
}
