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
import java.math.BigDecimal

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
    private val myr = "4.666894"
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
                    myr,
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
                    myr,
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
                    myr,
                ),
            ],
        )
        mockEcbRates(
            EcbMockUtils[
                "1999-01-04",
                EcbMockUtils.getRateMap(
                    usdEurRate,
                    usdSgdRate,
                    usdGpbRate,
                    "1.8855712953",
                    usdAudRate,
                    myr,
                ),
            ],
        )
        mockEcbRates(
            EcbMockUtils[
                "2019-10-18",
                mapOf(
                    Pair(Constants.NZD.code, BigDecimal("1.41030000")),
                ),
            ],
        )

        mockEcbRates(
            EcbMockUtils[
                "2021-10-18",
                EcbMockUtils.getRateMap(
                    usdEurRate,
                    usdSgdRate,
                    usdGpbRate,
                    "1.41030000",
                    usdAudRate,
                    myr,
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
                    myr,
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
                    myr,
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
                    myr,
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
                    usdNzdRate,
                    usdAudOtherRate,
                    myr,
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
