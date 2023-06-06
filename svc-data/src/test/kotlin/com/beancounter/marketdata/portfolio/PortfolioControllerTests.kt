package com.beancounter.marketdata.portfolio

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.Payload.Companion.DATA
import com.beancounter.common.contracts.PortfolioResponse
import com.beancounter.common.contracts.PortfoliosRequest
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.SGD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.utils.RegistrationUtils.registerUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.util.Locale
import java.util.UUID
import java.util.function.Consumer

private const val TEST_USER = "testUser"

/**
 * MVC Controller tests for Portfolios.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("slow")
@AutoConfigureMockMvc
@AutoConfigureMockAuth
@EnableWebSecurity
internal class PortfolioControllerTests {
    private val objectMapper = BcJson().objectMapper

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    private val portfolioRoot = "/portfolios"

    private val portfolioByCode = "$portfolioRoot/code/{code}"

    private val portfolioById = "$portfolioRoot/{id}"

    private lateinit var token: Jwt

    @Autowired
    fun registerUser() {
        token = registerUser(
            mockMvc,
            mockAuthConfig.getUserToken(SystemUser(TEST_USER, TEST_USER)),
        )
    }

    @Test
    fun ownerHonoured() {
        val userA = "UserA"
        val tokenA = registerUser(
            mockMvc,
            mockAuthConfig.getUserToken(SystemUser(userA, userA)),
        )
        val portfolioInput = PortfolioInput(
            UUID.randomUUID().toString().uppercase(Locale.getDefault()),
            "is_OwnerHonoured",
            USD.code,
            NZD.code,
        )
        var mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.post(portfolioRoot)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(tokenA))
                .content(
                    objectMapper
                        .writeValueAsBytes(PortfoliosRequest(setOf(portfolioInput))),
                )
                .contentType(MediaType.APPLICATION_JSON),
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()

        // User A creates a Portfolio
        val (data) = objectMapper
            .readValue(mvcResult.response.contentAsString, PortfoliosResponse::class.java)
        assertThat(data).hasSize(1)
        val portfolio = data.iterator().next()
        assertThat(portfolio).hasNoNullFieldsOrProperties()
        assertThat(portfolio.owner).hasFieldOrPropertyWithValue("id", tokenA.subject)
        log.debug("{}", portfolio.owner)

        // User A can see the portfolio they created
        mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.get(portfolioById, portfolio.id)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(tokenA)),
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        assertThat(
            objectMapper
                .readValue(mvcResult.response.contentAsString, PortfolioResponse::class.java)
                .data,
        ).hasNoNullFieldsOrProperties()

        // By code, also can
        mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.get(portfolioByCode, portfolioInput.code)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(tokenA)),
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        assertThat(
            objectMapper
                .readValue(mvcResult.response.contentAsString, PortfolioResponse::class.java)
                .data,
        ).hasNoNullFieldsOrProperties()

        // All users portfolios
        mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.get(portfolioRoot)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(tokenA)),
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        assertThat(
            objectMapper
                .readValue(
                    mvcResult.response.contentAsString,
                    PortfoliosResponse::class.java,
                ).data,
        ).hasSize(1)

        // User B, while a valid system user, cannot see UserA portfolios even if they know the ID
        val userB = "userB"
        val tokenB = registerUser(mockMvc, mockAuthConfig.getUserToken(SystemUser(userB, userB)))
        mockMvc.perform(
            MockMvcRequestBuilders.get(portfolioById, portfolio.id)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(tokenB)),
        ).andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andReturn()
        mockMvc.perform(
            MockMvcRequestBuilders.get(portfolioByCode, portfolio.code)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(tokenB)),
        ).andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andReturn()

        // All portfolios
        mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.get(portfolioRoot)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(tokenB)),
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn()
        assertThat(
            objectMapper
                .readValue(
                    mvcResult.response.contentAsString,
                    PortfoliosResponse::class.java,
                ).data,
        ).hasSize(0)
    }

    @Test
    fun notFoundByCode() {
        // Assert not found
        mockMvc.perform(
            MockMvcRequestBuilders.get(portfolioByCode, "does not exist")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON),
        ).andExpect(MockMvcResultMatchers.status().is4xxClientError)
            .andReturn()
    }

    @Test
    fun notFoundById() {
        mockMvc.perform(
            MockMvcRequestBuilders.get(portfolioById, "invalidId")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON),
        ).andExpect(MockMvcResultMatchers.status().is4xxClientError)
            .andReturn()
    }

    @Test
    fun findingByIdCode() {
        val portfolio = PortfolioInput(
            UUID.randomUUID().toString().uppercase(Locale.getDefault()),
            "is_findingByIdCode",
            USD.code,
            NZD.code,
        )

        val portfolioResult = mockMvc.perform(
            MockMvcRequestBuilders.post(portfolioRoot) // Mocking does not use the JwtRoleConverter configured in ResourceServerConfig
                .with(csrf())
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                .content(
                    objectMapper
                        .writeValueAsBytes(PortfoliosRequest(setOf(portfolio))),
                ).contentType(MediaType.APPLICATION_JSON),
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()

        val portfolios = objectMapper
            .readValue(portfolioResult.response.contentAsString, PortfoliosResponse::class.java)

        assertThat(portfolios)
            .isNotNull
            .hasFieldOrProperty(DATA)

        assertThat(portfolios.data)
            .hasSize(1)

        // Found by code
        var result = mockMvc.perform(
            MockMvcRequestBuilders.get(portfolioByCode, portfolio.code)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON),
        ).andExpect(
            MockMvcResultMatchers.status()
                .isOk,
        ).andExpect(
            MockMvcResultMatchers.content()
                .contentType(MediaType.APPLICATION_JSON),
        ).andReturn()
            .response.contentAsString

        val portfolioResponseByCode = objectMapper
            .readValue(result, PortfolioResponse::class.java)

        assertThat(portfolioResponseByCode)
            .isNotNull
            .hasNoNullFieldsOrProperties()

        result = mockMvc.perform(
            MockMvcRequestBuilders.get(portfolioById, portfolioResponseByCode.data.id)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON),
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn().response.contentAsString

        assertThat(objectMapper.readValue(result, PortfolioResponse::class.java))
            .usingRecursiveComparison().isEqualTo(portfolioResponseByCode)

        val mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.get(portfolioRoot)
                .with(csrf())
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                .contentType(MediaType.APPLICATION_JSON),
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()

        val (data) = objectMapper
            .readValue(mvcResult.response.contentAsString, PortfoliosResponse::class.java)

        assertThat(data).isNotEmpty
    }

    @Test
    fun persistAndFindPortfoliosWorking() {
        val portfolios: Collection<PortfolioInput> = arrayListOf(
            PortfolioInput(
                UUID.randomUUID().toString().uppercase(Locale.getDefault()),
                "is_persistAndFindPortfoliosWorking",
                USD.code,
                NZD.code,
            ),
        )
        val createRequest = PortfoliosRequest(portfolios)
        val portfolioResult = mockMvc.perform(
            MockMvcRequestBuilders.post(portfolioRoot)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                .with(csrf())
                .content(objectMapper.writeValueAsBytes(createRequest))
                .contentType(MediaType.APPLICATION_JSON),
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val portfoliosResponse = objectMapper
            .readValue(portfolioResult.response.contentAsString, PortfoliosResponse::class.java)
        assertThat(portfoliosResponse)
            .isNotNull
            .hasFieldOrProperty(DATA)
        assertThat(portfoliosResponse.data)
            .hasSize(1)
        portfoliosResponse.data
            .forEach(
                Consumer { foundPortfolio: Portfolio ->
                    assertThat(foundPortfolio)
                        .hasFieldOrProperty("id")
                        .hasFieldOrPropertyWithValue("code", portfolios.iterator().next().code)
                        .hasFieldOrPropertyWithValue(
                            "name",
                            portfolios.iterator().next().name,
                        )
                        .hasFieldOrPropertyWithValue("currency.code", portfolios.iterator().next().currency)
                        .hasFieldOrProperty("owner")
                        .hasFieldOrProperty("base")
                },
            )
    }

    @Test
    fun deletePortfolio() {
        val portfolioInput = PortfolioInput(
            UUID.randomUUID().toString().uppercase(Locale.getDefault()),
            "Delete Portfolio",
            USD.code,
            NZD.code,
        )
        // Add a token and repeat the call
        var mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.post(portfolioRoot)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                .with(csrf())
                .content(
                    objectMapper
                        .writeValueAsBytes(PortfoliosRequest(setOf(portfolioInput))),
                )
                .contentType(MediaType.APPLICATION_JSON),
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()

        // User A created a Portfolio
        val (data) = objectMapper
            .readValue(mvcResult.response.contentAsString, PortfoliosResponse::class.java)
        assertThat(data)
            .hasSize(1)
        val (id) = data.iterator().next()
        mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.delete(portfolioById, id)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON),
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn()
        assertThat(mvcResult.response.contentAsString).isEqualTo("ok")
    }

    @Test
    fun uniqueConstraintInPlace() {
        val portfolioInput = PortfolioInput(
            UUID.randomUUID().toString().uppercase(Locale.getDefault()),
            "is_UniqueConstraintInPlace",
            USD.code,
            NZD.code,
        )
        // Code and Owner are the same so, reject
        // Can't create two portfolios with the same code
        val result = mockMvc.perform(
            MockMvcRequestBuilders.post(portfolioRoot)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                .with(
                    csrf(),
                ).content(
                    objectMapper
                        .writeValueAsBytes(PortfoliosRequest(arrayListOf(portfolioInput, portfolioInput))),
                )
                .contentType(MediaType.APPLICATION_JSON),
        ).andExpect(MockMvcResultMatchers.status().isConflict)
            .andReturn()
        assertThat(result.resolvedException)
            .isNotNull
            .isInstanceOfAny(DataIntegrityViolationException::class.java)
    }

    @Test
    @WithMockUser(username = "unregisteredUser", authorities = [AuthConstants.SCOPE_BC])
    fun unregisteredUserRejected() {
        val portfolioInput = PortfolioInput(
            UUID.randomUUID().toString().uppercase(Locale.getDefault()),
            "is_UnregisteredUserRejected",
            USD.code,
            NZD.code,
        )
        // Authenticated, but unregistered user can't create portfolios
        mockMvc.perform(
            MockMvcRequestBuilders.post(portfolioRoot)
                .with(csrf())
                .content(
                    objectMapper
                        .writeValueAsBytes(PortfoliosRequest(arrayListOf(portfolioInput))),
                )
                .contentType(MediaType.APPLICATION_JSON),
        ).andExpect(MockMvcResultMatchers.status().isForbidden)
            .andReturn()
    }

    @Test
    @WithMockUser(username = TEST_USER, authorities = [AuthConstants.SCOPE_BC, AuthConstants.SCOPE_USER])
    fun updatePortfolio() {
        val createRequest = PortfoliosRequest(
            setOf(
                PortfolioInput(
                    UUID.randomUUID().toString().uppercase(Locale.getDefault()),
                    "is_UpdatePortfolioWorking",
                    USD.code,
                    NZD.code,
                ),
            ),
        )
        val portfolioResult = mockMvc.perform(
            MockMvcRequestBuilders.post(portfolioRoot)
                .with(csrf())
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                .content(objectMapper.writeValueAsBytes(createRequest))
                .contentType(MediaType.APPLICATION_JSON),
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val (data) = objectMapper
            .readValue(portfolioResult.response.contentAsString, PortfoliosResponse::class.java)
        val (id, _, _, _, _, owner) = data.iterator().next()
        val updateTo = PortfolioInput(
            "123",
            "Mikey",
            USD.code,
            SGD.code,
        )
        val patchResult = mockMvc.perform(
            MockMvcRequestBuilders.patch(portfolioById, id)
                .with(csrf())
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                .content(objectMapper.writeValueAsBytes(updateTo))
                .contentType(MediaType.APPLICATION_JSON),
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val (data1) = objectMapper
            .readValue(patchResult.response.contentAsString, PortfolioResponse::class.java)
        assertThat(owner).isNotNull
        // ID and SystemUser are immutable:
        assertThat(data1)
            .hasFieldOrPropertyWithValue("id", id)
            .hasFieldOrPropertyWithValue("name", updateTo.name)
            .hasFieldOrPropertyWithValue("code", updateTo.code)
            .hasFieldOrPropertyWithValue("currency.code", updateTo.currency)
            .hasFieldOrPropertyWithValue("base.code", updateTo.base)
            .hasFieldOrPropertyWithValue("owner.id", owner.id)
    }

    companion object {
        private val log = LoggerFactory.getLogger(PortfolioControllerTests::class.java)
    }
}
