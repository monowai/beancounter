package com.beancounter.marketdata.portfolio

import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.utils.BcMvcHelper
import com.beancounter.marketdata.utils.RegistrationUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.util.Locale
import java.util.UUID

/**
 * Verify portfolio ownership is respected between different user accounts.
 */
@SpringMvcDbTest
internal class PortfolioControllerOwnershipTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    val userA = "UserA"
    val userB = "userB"
    lateinit var tokenA: Jwt
    lateinit var tokenB: Jwt

    @Autowired
    fun setTokens(
        mockMvc: MockMvc,
        mockAuthConfig: MockAuthConfig
    ) {
        tokenA =
            RegistrationUtils.registerUser(
                mockMvc,
                mockAuthConfig.getUserToken(
                    SystemUser(
                        userA,
                        userA
                    )
                )
            )
        tokenB =
            RegistrationUtils.registerUser(
                mockMvc,
                mockAuthConfig.getUserToken(
                    SystemUser(
                        userB,
                        userB
                    )
                )
            )
    }

    @Test
    fun ownerHonoured() {
        val portfolioInput =
            PortfolioInput(
                UUID.randomUUID().toString().uppercase(Locale.getDefault()),
                "is_OwnerHonoured",
                Constants.USD.code,
                Constants.NZD.code
            )
        // User A creates a Portfolio
        val portfolio =
            BcMvcHelper
                .portfolioCreate(
                    portfolioInput,
                    mockMvc,
                    tokenA
                ).data
                .iterator()
                .next()
        assertThat(portfolio.owner).hasFieldOrPropertyWithValue(
            "email",
            tokenA.subject
        )
        // User B, while a valid system user, cannot see UserA portfolios even if they know the ID
        verifyPortfolioCantBeFound(
            portfolio,
            tokenB
        )

        // User A can see the portfolio they created
        assertThat(
            BcMvcHelper.portfolioById(
                portfolio.id,
                mockMvc,
                tokenA
            )
        ).hasNoNullFieldsOrProperties()

        // All users portfolios
        assertThat(
            BcMvcHelper.portfolios(
                mockMvc,
                tokenA
            )
        ).hasSize(1)

        // All portfolios
        assertThat(
            BcMvcHelper.portfolios(
                mockMvc,
                tokenB
            )
        ).hasSize(0)
    }

    private fun verifyPortfolioCantBeFound(
        portfolio: Portfolio,
        token: Jwt
    ) {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get(
                        BcMvcHelper.PORTFOLIO_BY_ID,
                        portfolio.id
                    ).with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
            ).andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andReturn()
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get(
                        BcMvcHelper.PORTFOLIO_BY_CODE,
                        portfolio.code
                    ).with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
            ).andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andReturn()
    }
}