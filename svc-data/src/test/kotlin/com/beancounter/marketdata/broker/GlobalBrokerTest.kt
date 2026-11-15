package com.beancounter.marketdata.broker

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.AuthConstants
import com.beancounter.auth.model.Registration
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.BrokerInput
import com.beancounter.common.model.Broker
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.utils.RegistrationUtils
import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private const val GLOBAL_BROKERS_ROOT = "/global-brokers"

/**
 * Global brokers are a catalogue every user reads and no user owns; a user's own
 * broker may name the global broker it is an account with.
 */
@SpringMvcDbTest
internal class GlobalBrokerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var globalBrokerRepository: GlobalBrokerRepository

    @Autowired
    private lateinit var brokerService: BrokerService

    @Autowired
    private lateinit var systemUserService: Registration

    private fun globalBroker(
        code: String,
        name: String,
        config: BrokerConfig = BrokerConfig()
    ) = globalBrokerRepository.save(GlobalBroker(code = code, name = name, config = config))

    @Test
    fun `should list global brokers by name with their configuration`() {
        globalBroker("LIST-Z", "Zulu Trading")
        val alpha =
            globalBroker(
                "LIST-A",
                "Alpha Securities",
                BrokerConfig(aliases = listOf("Alpha", "Old Alpha"), market = "US", currency = "USD")
            )
        val token = RegistrationUtils.registerUser(mockMvc, mockAuthConfig.getUserToken(SystemUser()))

        val listed =
            mockMvc
                .perform(get(GLOBAL_BROKERS_ROOT).with(jwt().jwt(token)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data[?(@.code == 'LIST-A')].id").value(alpha.id))
                .andExpect(jsonPath("$.data[?(@.code == 'LIST-A')].name").value("Alpha Securities"))
                .andExpect(jsonPath("$.data[?(@.code == 'LIST-A')].config.aliases[1]").value("Old Alpha"))
                .andExpect(jsonPath("$.data[?(@.code == 'LIST-A')].config.market").value("US"))
                .andExpect(jsonPath("$.data[?(@.code == 'LIST-A')].config.currency").value("USD"))
                .andReturn()

        val names = JsonPath.read<List<String>>(listed.response.contentAsString, "$.data[*].name")
        assertThat(names.indexOf("Alpha Securities")).isLessThan(names.indexOf("Zulu Trading"))
    }

    @Test
    fun `should refuse a token without user or system authority`() {
        val token = RegistrationUtils.registerUser(mockMvc, mockAuthConfig.getUserToken(SystemUser()))

        mockMvc
            .perform(
                get(GLOBAL_BROKERS_ROOT)
                    .with(jwt().jwt(token).authorities(SimpleGrantedAuthority(AuthConstants.SCOPE_ADMIN)))
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `should record the global broker a user's broker is an account with`() {
        val owner = SystemUser(id = "global-link-owner", email = "global-link-owner@test.com")
        mockAuthConfig.login(owner, systemUserService)
        val first = globalBroker("LINK-1", "Link One")
        val second = globalBroker("LINK-2", "Link Two")

        val created =
            brokerService.create(Broker(name = "My account", owner = owner, globalBrokerId = first.id))
        assertThat(brokerService.findByIdWithAccounts(created.id, owner).get().globalBrokerId)
            .isEqualTo(first.id)

        brokerService.update(created.id, owner, BrokerInput(name = "My account", globalBrokerId = second.id))
        assertThat(brokerService.findById(created.id, owner).get().globalBrokerId).isEqualTo(second.id)

        brokerService.update(created.id, owner, BrokerInput(name = "Renamed"))
        assertThat(brokerService.findById(created.id, owner).get().globalBrokerId).isEqualTo(second.id)
    }

    @Test
    fun `should refuse a broker that names an unknown global broker`() {
        val owner = SystemUser(id = "global-unknown-owner", email = "global-unknown-owner@test.com")
        mockAuthConfig.login(owner, systemUserService)

        assertThatThrownBy {
            brokerService.create(Broker(name = "Create", owner = owner, globalBrokerId = "nobody"))
        }.isInstanceOf(BusinessException::class.java)
            .hasMessage("Global broker not found: nobody")
        assertThat(brokerService.findByOwner(owner)).isEmpty()

        val broker = brokerService.create(Broker(name = "Update", owner = owner))
        assertThatThrownBy {
            brokerService.update(broker.id, owner, BrokerInput(name = "Update", globalBrokerId = "nobody"))
        }.isInstanceOf(BusinessException::class.java)
            .hasMessage("Global broker not found: nobody")
    }
}