package com.beancounter.marketdata.portfolio

import com.beancounter.auth.common.TokenUtils
import com.beancounter.auth.server.AuthorityRoleConverter
import com.beancounter.common.contracts.Payload.Companion.DATA
import com.beancounter.common.contracts.PortfolioResponse
import com.beancounter.common.contracts.PortfoliosRequest
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.exception.ForbiddenException
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.SGD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.utils.RegistrationUtils.registerUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.Locale
import java.util.UUID
import java.util.function.Consumer

@SpringBootTest
@ActiveProfiles("test")
@Tag("slow")
@WebAppConfiguration
/**
 * MVC Controller tests for Portfolios.
 */
internal class PortfolioMvcTests {
    private val objectMapper = BcJson().objectMapper
    private val authorityRoleConverter = AuthorityRoleConverter()
    private val tokenUtils = TokenUtils()
    val token = tokenUtils.getUserToken(SystemUser(UUID.randomUUID().toString()))
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var context: WebApplicationContext

    @BeforeEach
    fun mockServices() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
            .build()
    }

    private val portfolioRoot = "/portfolios"

    private val portfolioByCode = "$portfolioRoot/code/{code}"

    private val portfolioById = "$portfolioRoot/{id}"

    @Test
    fun notFoundByCode() {
        // Assert not found
        val token = tokenUtils.getUserToken(SystemUser(UUID.randomUUID().toString()))
        mockMvc.perform(
            MockMvcRequestBuilders.get(portfolioByCode, "does not exist")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().is4xxClientError)
            .andReturn()
    }

    @Test
    fun notFoundById() {
        val token = tokenUtils.getUserToken(SystemUser(UUID.randomUUID().toString()))
        mockMvc.perform(
            MockMvcRequestBuilders.get(portfolioById, "invalidId")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().is4xxClientError)
            .andReturn()
    }

    @Test
    @Throws(Exception::class)
    fun findingByIdCode() {
        val portfolio = PortfolioInput(
            UUID.randomUUID().toString().uppercase(Locale.getDefault()),
            "is_findingByIdCode",
            USD.code,
            NZD.code
        )

        registerUser(mockMvc, token)
        val portfolioResult = mockMvc.perform(
            MockMvcRequestBuilders.post(portfolioRoot) // Mocking does not use the JwtRoleConverter configured in ResourceServerConfig
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                .content(
                    objectMapper
                        .writeValueAsBytes(PortfoliosRequest(setOf(portfolio)))
                ).contentType(MediaType.APPLICATION_JSON)
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
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(
            MockMvcResultMatchers.status()
                .isOk
        ).andExpect(
            MockMvcResultMatchers.content()
                .contentType(MediaType.APPLICATION_JSON)
        ).andReturn()
            .response.contentAsString

        val portfolioResponseByCode = objectMapper
            .readValue(result, PortfolioResponse::class.java)

        assertThat(portfolioResponseByCode)
            .isNotNull
            .hasNoNullFieldsOrProperties()

        result = mockMvc.perform(
            MockMvcRequestBuilders.get(portfolioById, portfolioResponseByCode.data.id)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn().response.contentAsString

        assertThat(objectMapper.readValue(result, PortfolioResponse::class.java))
            .usingRecursiveComparison().isEqualTo(portfolioResponseByCode)

        val mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.get(portfolioRoot)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()

        val (data) = objectMapper
            .readValue(mvcResult.response.contentAsString, PortfoliosResponse::class.java)

        assertThat(data).isNotEmpty
    }

    @Test
    @Throws(Exception::class)
    fun persistAndFindPortfoliosWorking() {
        registerUser(mockMvc, token)
        val portfolios: Collection<PortfolioInput> = arrayListOf(
            PortfolioInput(
                UUID.randomUUID().toString().uppercase(Locale.getDefault()),
                "is_persistAndFindPortfoliosWorking",
                USD.code,
                NZD.code
            )
        )
        val createRequest = PortfoliosRequest(portfolios)
        val portfolioResult = mockMvc.perform(
            MockMvcRequestBuilders.post(portfolioRoot)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                .content(objectMapper.writeValueAsBytes(createRequest))
                .contentType(MediaType.APPLICATION_JSON)
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
                            portfolios.iterator().next().name
                        )
                        .hasFieldOrPropertyWithValue("currency.code", portfolios.iterator().next().currency)
                        .hasFieldOrProperty("owner")
                        .hasFieldOrProperty("base")
                }
            )
    }

    @Test
    @Throws(Exception::class)
    fun ownerHonoured() {
        val userA = SystemUser(UUID.randomUUID().toString())
        val portfolioInput = PortfolioInput(
            UUID.randomUUID().toString().uppercase(Locale.getDefault()),
            "is_OwnerHonoured",
            USD.code,
            NZD.code
        )
        mockMvc.perform(
            MockMvcRequestBuilders.post(portfolioRoot) // No Token
                .content(
                    objectMapper
                        .writeValueAsBytes(PortfoliosRequest(setOf(portfolioInput)))
                )
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isForbidden)
            .andReturn()

        // Add a token and repeat the call
        val tokenA = tokenUtils.getUserToken(userA)
        registerUser(mockMvc, tokenA)
        var mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.post(portfolioRoot)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(tokenA).authorities(authorityRoleConverter))
                .content(
                    objectMapper
                        .writeValueAsBytes(PortfoliosRequest(setOf(portfolioInput)))
                )
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()

        // User A creates a Portfolio
        val (data) = objectMapper
            .readValue(mvcResult.response.contentAsString, PortfoliosResponse::class.java)
        assertThat(data)
            .hasSize(1)
        val portfolio = data.iterator().next()
        assertThat(portfolio).hasNoNullFieldsOrProperties()
        assertThat(portfolio.owner).hasFieldOrPropertyWithValue("id", userA.id)
        log.debug("{}", portfolio.owner)

        // User A can see the portfolio they created
        mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.get(portfolioById, portfolio.id)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(tokenA).authorities(authorityRoleConverter))
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        assertThat(
            objectMapper
                .readValue(mvcResult.response.contentAsString, PortfolioResponse::class.java)
                .data
        ).hasNoNullFieldsOrProperties()

        // By code, also can
        mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.get(portfolioByCode, portfolioInput.code)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(tokenA).authorities(authorityRoleConverter))
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        assertThat(
            objectMapper
                .readValue(mvcResult.response.contentAsString, PortfolioResponse::class.java)
                .data
        )
            .hasNoNullFieldsOrProperties()

        // All users portfolios
        mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.get(portfolioRoot)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(tokenA).authorities(authorityRoleConverter))
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        assertThat(
            objectMapper
                .readValue(
                    mvcResult.response.contentAsString, PortfoliosResponse::class.java
                ).data
        )
            .hasSize(1)

        // User B, while a valid system user, cannot see UserA portfolios even if they know the ID
        val userB = SystemUser("user2", "user2@testing.com")
        val tokenB = tokenUtils.getUserToken(userB)
        registerUser(mockMvc, tokenB)
        mockMvc.perform(
            MockMvcRequestBuilders.get(portfolioById, portfolio.id)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(tokenB).authorities(authorityRoleConverter))
        ).andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andReturn()
        mockMvc.perform(
            MockMvcRequestBuilders.get(portfolioByCode, portfolio.code)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(tokenB).authorities(authorityRoleConverter))
        ).andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andReturn()

        // All portfolios
        mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.get("/portfolios/")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(tokenB).authorities(authorityRoleConverter))
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn()
        assertThat(
            objectMapper
                .readValue(
                    mvcResult.response.contentAsString, PortfoliosResponse::class.java
                ).data
        )
            .hasSize(0)
    }

    @Test
    @Throws(Exception::class)
    fun deletePortfolio() {
        val portfolioInput = PortfolioInput(
            UUID.randomUUID().toString().uppercase(Locale.getDefault()),
            "Delete Portfolio",
            USD.code,
            NZD.code
        )
        val userA = SystemUser(UUID.randomUUID().toString())

        // Add a token and repeat the call
        val token = tokenUtils.getUserToken(userA)
        registerUser(mockMvc, token)
        var mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.post(portfolioRoot)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                .content(
                    objectMapper
                        .writeValueAsBytes(PortfoliosRequest(setOf(portfolioInput)))
                )
                .contentType(MediaType.APPLICATION_JSON)
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
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn()
        assertThat(mvcResult.response.contentAsString).isEqualTo("ok")
    }

    @Test
    @Throws(Exception::class)
    fun uniqueConstraintInPlace() {
        val portfolioInput = PortfolioInput(
            UUID.randomUUID().toString().uppercase(Locale.getDefault()),
            "is_UniqueConstraintInPlace",
            USD.code,
            NZD.code
        )
        val portfolios: MutableCollection<PortfolioInput> = ArrayList()
        portfolios.add(portfolioInput)
        portfolios.add(portfolioInput) // Code and Owner are the same so, reject
        val userA = SystemUser(UUID.randomUUID().toString())
        val token = tokenUtils.getUserToken(userA)
        registerUser(mockMvc, token)
        // Can't create two portfolios with the same code
        val result = mockMvc.perform(
            MockMvcRequestBuilders.post(portfolioRoot)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter)
                ).content(
                    objectMapper
                        .writeValueAsBytes(PortfoliosRequest(portfolios))
                )
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isConflict)
            .andReturn()
        assertThat(result.resolvedException)
            .isNotNull
            .isInstanceOfAny(DataIntegrityViolationException::class.java)
    }

    @Test
    @Throws(Exception::class)
    fun unregisteredUserRejected() {
        val portfolioInput = PortfolioInput(
            UUID.randomUUID().toString().uppercase(Locale.getDefault()),
            "is_UnregisteredUserRejected",
            USD.code,
            NZD.code
        )
        val portfolios: MutableCollection<PortfolioInput> = ArrayList()
        portfolios.add(portfolioInput)
        val userA = SystemUser(UUID.randomUUID().toString())
        val token = tokenUtils.getUserToken(userA)
        // registerUser(mockMvc, token);
        // Authenticated, but unregistered user can't create portfolios
        val result = mockMvc.perform(
            MockMvcRequestBuilders.post(portfolioRoot)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter)
                ).content(
                    objectMapper
                        .writeValueAsBytes(PortfoliosRequest(portfolios))
                )
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isForbidden)
            .andReturn()
        assertThat(result.resolvedException)
            .isNotNull
            .isInstanceOfAny(ForbiddenException::class.java)
    }

    @Test
    @Throws(Exception::class)
    fun updatePortfolio() {
        val user = SystemUser(UUID.randomUUID().toString())
        val token = tokenUtils.getUserToken(user)
        registerUser(mockMvc, token)
        val portfolios: MutableCollection<PortfolioInput> = ArrayList()
        val portfolioInput = PortfolioInput(
            UUID.randomUUID().toString().uppercase(Locale.getDefault()),
            "is_UpdatePortfolioWorking",
            USD.code,
            NZD.code
        )
        portfolios.add(portfolioInput)
        val createRequest = PortfoliosRequest(portfolios)
        val portfolioResult = mockMvc.perform(
            MockMvcRequestBuilders.post(portfolioRoot)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                .content(objectMapper.writeValueAsBytes(createRequest))
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val (data) = objectMapper
            .readValue(portfolioResult.response.contentAsString, PortfoliosResponse::class.java)
        val (id, _, _, _, _, owner) = data.iterator().next()
        val updateTo = PortfolioInput(
            "123", "Mikey", USD.code, SGD.code
        )
        val patchResult = mockMvc.perform(
            MockMvcRequestBuilders.patch(portfolioById, id)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                .content(objectMapper.writeValueAsBytes(updateTo))
                .contentType(MediaType.APPLICATION_JSON)
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
            .hasFieldOrPropertyWithValue("owner.id", owner!!.id)
    }

    companion object {
        private val log = LoggerFactory.getLogger(PortfolioMvcTests::class.java)
    }
}
