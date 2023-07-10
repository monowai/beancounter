package com.contracts.data

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

/**
 * Base class for FX Contract tests
 */
class FxBase : ContractVerifierBase() {
    @MockBean
    private lateinit var fxGateway: FxGateway

    @Autowired
    private lateinit var dateUtils: DateUtils

    private val eur = "0.8973438622"
    private val sgd = "1.3652189519"
    private val gbp = "0.7756191673"
    private val nzd = "1.5692749462"
    private val aud = "1.4606963388"

    @MockBean
    internal lateinit var cashBalancesBean: CashBalancesBean

    @BeforeEach
    fun setupRates() {
        mockConstantRates()
        fridaySaturday()
        explicitRatesAsAt()
        mockEcbRates(
            EcbMockUtils[
                "2019-01-01",
                EcbMockUtils.getRateMap(
                    eur,
                    sgd,
                    gbp,
                    "10.0",
                    aud,
                ),
            ],
        )
    }

    private fun explicitRatesAsAt() {
        mockEcbRates(
            EcbMockUtils[
                "2019-08-16",
                EcbMockUtils.getRateMap(
                    "0.9028530155",
                    "1.3864210906",
                    "0.8218941856",
                    "1.5536294691",
                    "1.4734561213",
                ),
            ],
        )

        mockEcbRates(
            EcbMockUtils[
                "2019-11-12",
                EcbMockUtils.getRateMap(
                    "0.9078529278",
                    "1.36123468",
                    "0.7791193827",
                    "1.5780299591",
                    "1.460463005",
                ),
            ],
        )
        mockEcbRates(
            EcbMockUtils[
                "1999-01-04",
                EcbMockUtils.getRateMap(
                    "0.8482483671",
                    "1.6586648571",
                    "0.6031894139",
                    "1.8855712953",
                    "1.6201543812",
                ),
            ],
        )

        mockEcbRates(
            EcbMockUtils[
                "2021-10-18",
                EcbMockUtils.getRateMap(
                    "0.8482483671",
                    "1.6586648571",
                    "0.6031894139",
                    "1.41030000",
                    "1.6201543812",
                ),
            ],
        )
    }

    private fun mockConstantRates() {
        mockEcbRates(
            EcbMockUtils[
                "2019-10-20",
                EcbMockUtils.getRateMap(
                    eur,
                    sgd,
                    gbp,
                    nzd,
                    aud,
                ),
            ],
        )

        mockEcbRates(
            EcbMockUtils[
                rateDate,
                EcbMockUtils.getRateMap(
                    eur,
                    sgd,
                    gbp,
                    nzd,
                    aud,
                ),
            ],
        )
        // Current
        mockEcbRates(
            EcbMockUtils[
                dateUtils.today(),
                EcbMockUtils.getRateMap(
                    eur,
                    sgd,
                    gbp,
                    nzd,
                    aud,
                ),
            ],
        )
    }

    private fun fridaySaturday() {
        mockEcbRates(
            EcbMockUtils[
                "2019-07-26",
                EcbMockUtils.getRateMap(
                    "0.897827258",
                    "1.3684683067",
                    "0.8047495062",
                    "1.5053869635",
                    "1.4438857964",
                ),
            ],
        )
        // Saturday's results are the same as Fridays
        mockEcbRates(
            EcbMockUtils[
                "2019-07-26",
                EcbMockUtils.getRateMap(
                    "0.897827258",
                    "1.3684683067",
                    "0.8047495062",
                    "1.5053869635",
                    "1.4438857964",
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
                java.lang.String.join(",", exRatesResponse.rates.keys),
            ),
        ).thenReturn(exRatesResponse)
    }
}
