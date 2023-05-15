package com.beancounter.position.integ

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.position.Constants
import com.beancounter.position.Constants.Companion.owner
import com.beancounter.position.service.PositionService
import com.beancounter.position.valuation.ValuationService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal

private const val pCostBasis = "costBasis"

/**
 * Verify assumptions around how Real estate transactions behave.
 */
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = [
        "org.beancounter:svc-data:+:stubs:10999",
    ],
)
@Tag("slow")
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMockAuth
class RealEstateTest {
    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var wac: WebApplicationContext

    @Autowired
    private lateinit var positionService: PositionService

    @Autowired
    private lateinit var valuationService: ValuationService

    var portfolio: Portfolio = Portfolio(
        id = "RE-TEST",
        code = "RE-TEST",
        name = "RE-TEST Portfolio",
        currency = Constants.USD,
        base = Constants.USD,
        owner = owner,
    )
    val date = "2023-05-01"

    @BeforeEach
    fun mockLogin() {
        mockAuthConfig.mockLogin()
    }

    @Test
    fun runTest() {
        val results = valuationService.build(portfolio, date)
        assertThat(results.data).isNotNull
        assertThat(results.data.positions).hasSize(3)
        for (position in results.data.positions) {
            when (position.key) {
                "MORTGAGE 1:CASH" -> {
                    assertThat(position.value.moneyValues[Position.In.TRADE])
                        .hasFieldOrPropertyWithValue(pCostBasis, BigDecimal("-10000.00"))
                }
                "MORTGAGE 2:CASH" -> {
                    assertThat(position.value.moneyValues[Position.In.TRADE])
                        .hasFieldOrPropertyWithValue(pCostBasis, BigDecimal("-10000.00"))
                }
                "USD.RE:PRIVATE" -> {
                    assertThat(position.value.moneyValues[Position.In.TRADE])
                        .hasFieldOrPropertyWithValue(pCostBasis, BigDecimal("10000.00"))
                }
            }
        }
    }
}
