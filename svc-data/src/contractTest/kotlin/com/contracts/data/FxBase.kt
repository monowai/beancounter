package com.contracts.data

import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.fx.EcbMockUtils
import com.beancounter.marketdata.fx.fxrates.ExRatesResponse
import com.beancounter.marketdata.fx.fxrates.FxGateway
import com.beancounter.marketdata.trn.cash.CashBalancesBean
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import java.math.BigDecimal

/**
 * Provides a foundational setup for conducting foreign exchange (FX) contract verification tests.
 * This class extends {@link ContractVerifierBase} to include setup and utilities specifically tailored
 * for testing FX rate fetching and processing functionalities.
 *
 * <p>It prepares a mock environment to simulate FX rate responses from external services, specifically
 * leveraging mocked responses from an {@link FxGateway}. This allows for controlled testing of FX rate
 * retrieval and calculation across various scenarios and time points.</p>
 *
 * <p>The class sets up historical and real-time exchange rate data for multiple currency pairs,
 * facilitating the verification of FX-related functionalities in the application. It handles the initialization
 * of {@link DateUtils} for date manipulations and mocks FX rate responses for predefined dates using {@link EcbMockUtils}.</p>
 *
 * <p>Key functionalities:</p>
 * <ul>
 *   <li>Initializes and injects mock responses for an {@link FxGateway} to simulate FX rate fetching.</li>
 *   <li>Uses {@link DateUtils} to manipulate and provide date contexts for rate fetching, ensuring tests
 *       reflect real-world scenarios where date-specific data is crucial.</li>
 *   <li>Configures a comprehensive set of exchange rates for major currency pairs across multiple historical
 *       and current dates to test various FX rate lookup scenarios.</li>
 *   <li>Ensures the consistency of FX rate data over weekends, where rates from Friday are used for Saturday,
 *       reflecting typical FX market behavior.</li>
 * </ul>
 *
 * <p>This setup is crucial for ensuring that the application handles FX data correctly under various
 * conditions, making it an essential component of the financial data processing and verification suite.</p>
 */
class FxBase : ContractVerifierBase() {
    @MockBean
    private lateinit var fxGateway: FxGateway

    @MockBean
    private lateinit var fxTransactions: FxTransactions

    @Autowired
    private lateinit var dateUtils: DateUtils

    private val eur = "0.8973438622"
    private val sgd = "1.3652189519"
    private val gbp = "0.7756191673"
    private val nzd = "1.5692749462"
    private val aud = "1.4606963388"
    private val myr = "4.666894"
    private val usdSgd = "1.3684683067"
    private val usdGbp = "0.8047495062"
    private val usdEur = "0.897827258"
    private val friday = "2019-07-26"
    private val usdAudRate = "1.6201543812"
    private val usdEurRate = "0.8482483671"
    private val usdSgdRate = "1.6586648571"
    private val usdGpbRate = "0.6031894139"
    private val usdAudOtherRate = "1.4438857964"
    private val usdNzdRate = "1.5053869635"

    @MockBean
    internal lateinit var cashBalancesBean: CashBalancesBean

    @BeforeEach
    fun setupRates() {
        val historicalRates =
            mapOf(
                "2019-08-16" to
                    EcbMockUtils.getRateMap(
                        "0.9028530155",
                        "1.3864210906",
                        "0.8218941856",
                        "1.5536294691",
                        "1.4734561213",
                        myr,
                    ),
                "2019-11-12" to
                    EcbMockUtils.getRateMap(
                        "0.9078529278",
                        "1.36123468",
                        "0.7791193827",
                        "1.5780299591",
                        "1.460463005",
                        myr,
                    ),
                "1999-01-04" to
                    EcbMockUtils.getRateMap(
                        usdEurRate,
                        usdSgdRate,
                        usdGpbRate,
                        "1.8855712953",
                        usdAudRate,
                        myr,
                    ),
                "2019-10-18" to
                    mapOf(
                        Pair(Constants.NZD.code, BigDecimal("1.41030000")),
                    ),
                "2021-10-18" to
                    EcbMockUtils.getRateMap(
                        usdEurRate,
                        usdSgdRate,
                        usdGpbRate,
                        "1.41030000",
                        usdAudRate,
                        myr,
                    ),
                "2019-10-20" to
                    EcbMockUtils.getRateMap(
                        eur,
                        sgd,
                        gbp,
                        nzd,
                        aud,
                        myr,
                    ),
                rateDate to
                    EcbMockUtils.getRateMap(
                        eur,
                        sgd,
                        gbp,
                        nzd,
                        aud,
                        myr,
                    ),
                dateUtils.today() to
                    EcbMockUtils.getRateMap(
                        eur,
                        sgd,
                        gbp,
                        nzd,
                        aud,
                        myr,
                    ),
                friday to
                    EcbMockUtils.getRateMap(
                        usdEur,
                        usdSgd,
                        usdGbp,
                        usdNzdRate,
                        usdAudOtherRate,
                        myr,
                    ),
                "2019-01-01" to
                    EcbMockUtils.getRateMap(
                        eur,
                        sgd,
                        gbp,
                        "10.0",
                        aud,
                        myr,
                    ),
            )
        historicalRates.forEach { (date, rates) ->
            mockEcbRates(EcbMockUtils[date, rates])
        }
        // Saturday's result values are exactly the same as Fridays
        mockEcbRates(
            EcbMockUtils[
                friday,
                EcbMockUtils.getRateMap(
                    usdEur,
                    usdSgd,
                    usdGbp,
                    usdNzdRate,
                    usdAudOtherRate,
                    myr,
                ),
            ],
            "2019-07-27",
        )
    }

    private fun mockEcbRates(
        exRatesResponse: ExRatesResponse,
        rateDate: String = exRatesResponse.date.toString(),
    ) {
        Mockito.`when`(
            fxGateway.getRatesForSymbols(
                rateDate,
                Constants.USD.code,
                exRatesResponse.rates.keys.joinToString(","),
            ),
        ).thenReturn(exRatesResponse)
    }
}
