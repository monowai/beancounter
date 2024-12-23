package com.beancounter.position.integ

import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.position.Constants
import com.beancounter.position.Constants.Companion.PROP_COST_BASIS
import com.beancounter.position.Constants.Companion.owner
import com.beancounter.position.StubbedTest
import com.beancounter.position.valuation.ValuationService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal

/**
 * Verify assumptions around how Real estate transactions behave.
 */
@StubbedTest
class RealEstateTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var valuationService: ValuationService

    var portfolio: Portfolio =
        Portfolio(
            id = "RE-TEST",
            code = "RE-TEST",
            name = "RE-TEST Portfolio",
            currency = Constants.USD,
            base = Constants.USD,
            owner = owner
        )
    val date = "2023-05-01"

    @BeforeEach
    fun mockLogin() {
        mockAuthConfig.login()
    }

    @Test
    fun `values real estate with no market value`() {
        val results =
            valuationService.build(
                portfolio,
                date
            )

        // Assert: Check overall results data integrity
        assertThat(results.data)
            .describedAs("Results data should not be null")
            .isNotNull

        // Assert: Validate the size of the positions
        assertThat(results.data.positions)
            .describedAs("Should have exactly 3 positions")
            .hasSize(3)

        val expectedCost = BigDecimal("-10000.00")
        val expectedCosts =
            mapOf(
                "MORTGAGE 1:CASH" to expectedCost,
                "MORTGAGE 2:CASH" to expectedCost,
                "USD.RE:PRIVATE" to expectedCost.abs()
            )

        results.data.positions.forEach { position ->
            expectedCosts[position.key]?.let { expectedCostValue ->
                assertThat(position.value.moneyValues[Position.In.TRADE])
                    .describedAs("Cost basis for ${position.key} should be correct")
                    .hasFieldOrPropertyWithValue(
                        PROP_COST_BASIS,
                        expectedCostValue
                    )
            }
        }
    }
}