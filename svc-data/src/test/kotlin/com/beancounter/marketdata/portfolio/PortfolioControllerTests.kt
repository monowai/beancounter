package com.beancounter.marketdata.portfolio

import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.contracts.Payload.Companion.DATA
import com.beancounter.common.contracts.PortfolioResponse
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.SGD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.utils.BcMvcHelper.Companion.portfolioByCode
import com.beancounter.marketdata.utils.BcMvcHelper.Companion.portfolioById
import com.beancounter.marketdata.utils.BcMvcHelper.Companion.portfolioCreate
import com.beancounter.marketdata.utils.PORTFOLIO_ROOT
import com.beancounter.marketdata.utils.RegistrationUtils.registerUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.util.Locale
import java.util.UUID
import java.util.function.Consumer

private const val P_CODE = "code"

const val P_ID = "id"

private const val P_NAME = "name"

private const val P_CCY_CODE = "currency.code"

/**
 * MVC Controller tests for Portfolios.
 */
@SpringMvcDbTest
internal class PortfolioControllerTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    private lateinit var token: Jwt

    // @BeforeEach
    // fun configure() {
    //     token = mockAuthConfig.login("xx@xx.com")
    // }
    @Autowired
    fun getToken(
        mockMvc: MockMvc,
        mockAuthConfig: MockAuthConfig
    ): Jwt {
        token =
            registerUser(
                mockMvc,
                mockAuthConfig.getUserToken(SystemUser("portfolioControllerTests"))
            )
        return token
    }

    @Test
    fun findingByIdCode() {
        val portfolioInput =
            PortfolioInput(
                code = "is_findingByIdCode",
                USD.code,
                NZD.code
            )
        val portfolioResponse =
            portfolioCreate(
                portfolioInput,
                mockMvc,
                token
            )
        assertThat(portfolioResponse.data).hasSize(1)
        val portfolio = portfolioResponse.data.iterator().next()

        // Found by code
        val portfolioResponseByCode =
            portfolioByCode(
                portfolio.code,
                mockMvc,
                token
            )

        assertThat(portfolioResponseByCode)
            .isNotNull
            .hasNoNullFieldsOrProperties()

        assertThat(
            portfolioById(
                portfolio.id,
                mockMvc,
                token
            )
        ).usingRecursiveComparison().isEqualTo(portfolioResponseByCode.data)

        assertThat(
            objectMapper
                .readValue(
                    mockMvc
                        .perform(
                            MockMvcRequestBuilders
                                .get(PORTFOLIO_ROOT)
                                .with(csrf())
                                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                                .contentType(MediaType.APPLICATION_JSON)
                        ).andExpect(MockMvcResultMatchers.status().isOk)
                        .andExpect(
                            MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON)
                        ).andReturn()
                        .response.contentAsString,
                    PortfoliosResponse::class.java
                ).data
        ).isNotEmpty
    }

    @Test
    fun persistAndFindPortfoliosWorking() {
        val portfolios: Collection<PortfolioInput> =
            arrayListOf(
                PortfolioInput(
                    UUID.randomUUID().toString().uppercase(Locale.getDefault()),
                    "is_persistAndFindPortfoliosWorking",
                    USD.code,
                    NZD.code
                )
            )

        val portfoliosResponse =
            portfolioCreate(
                portfolios,
                mockMvc,
                token
            )

        assertThat(portfoliosResponse)
            .isNotNull
            .hasFieldOrProperty(DATA)
        assertThat(portfoliosResponse.data)
            .hasSize(1)
        portfoliosResponse.data
            .forEach(
                Consumer { foundPortfolio: Portfolio ->
                    assertThat(foundPortfolio)
                        .hasFieldOrProperty(P_ID)
                        .hasFieldOrPropertyWithValue(
                            P_CODE,
                            portfolios.iterator().next().code
                        ).hasFieldOrPropertyWithValue(
                            P_NAME,
                            portfolios.iterator().next().name
                        ).hasFieldOrPropertyWithValue(
                            P_CCY_CODE,
                            portfolios.iterator().next().currency
                        ).hasFieldOrProperty("owner")
                        .hasFieldOrProperty("base")
                        .hasFieldOrPropertyWithValue(
                            "marketValue",
                            java.math.BigDecimal.ZERO
                        ).hasFieldOrPropertyWithValue(
                            "irr",
                            java.math.BigDecimal.ZERO
                        )
                }
            )
    }

    @Test
    fun deletePortfolio() {
        val portfolioInput =
            PortfolioInput(
                UUID.randomUUID().toString().uppercase(Locale.getDefault()),
                "Delete Portfolio",
                USD.code,
                NZD.code
            )

        val created =
            portfolioCreate(
                portfolioInput,
                mockMvc,
                token
            ).data
        assertThat(created)
            .hasSize(1)
        val id = created.iterator().next().id
        val mvcResult =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .delete(
                            "$PORTFOLIO_ROOT/{id}",
                            id
                        ).with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()
        assertThat(mvcResult.response.contentAsString).isEqualTo("deleted $id")
    }

    @Test
    fun updatePortfolio() {
        val portfolioResponse =
            portfolioCreate(
                PortfolioInput(
                    UUID.randomUUID().toString().uppercase(Locale.getDefault()),
                    "updatePortfolio",
                    USD.code,
                    NZD.code
                ),
                mockMvc,
                token
            ).data

        val (id, _, _, _, _, _, _, owner) = portfolioResponse.iterator().next()
        val updateTo =
            PortfolioInput(
                "UPDATE_TO_THIS",
                "Mikey",
                USD.code,
                SGD.code
            )
        val patchResult =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .patch(
                            "$PORTFOLIO_ROOT/{id}",
                            id
                        ).with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                        .content(objectMapper.writeValueAsBytes(updateTo))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val (data) =
            objectMapper
                .readValue(
                    patchResult.response.contentAsString,
                    PortfolioResponse::class.java
                )
        assertThat(owner).isNotNull
        // ID and SystemUser are immutable:
        assertThat(data)
            .hasFieldOrPropertyWithValue(
                P_ID,
                id
            ).hasFieldOrPropertyWithValue(
                P_NAME,
                updateTo.name
            ).hasFieldOrPropertyWithValue(
                P_CODE,
                updateTo.code
            ).hasFieldOrPropertyWithValue(
                P_CCY_CODE,
                updateTo.currency
            ).hasFieldOrPropertyWithValue(
                "base.code",
                updateTo.base
            ).hasFieldOrPropertyWithValue(
                "owner.id",
                owner.id
            )
    }
}