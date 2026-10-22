package com.beancounter.agent.quota

import com.beancounter.agent.conversation.ConversationOwner
import com.beancounter.agent.conversation.ConversationTitler
import com.beancounter.auth.model.AuthConstants
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * The quota REST surface: a caller reads their own quota; only an
 * administrator reads or sets another user's limit. Only the svc-data `/me`
 * lookup is stubbed.
 */
@SpringBootTest(
    properties = [
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://beancounter.eu.auth0.com/",
        "auth.audience=https://holdsworth.app"
    ]
)
@AutoConfigureMockMvc
class AgentQuotaControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var quota: AgentQuota

    @MockitoBean
    private lateinit var owner: ConversationOwner

    @MockitoBean
    private lateinit var titler: ConversationTitler

    private val me = "quota-rest-me"
    private val other = "quota-rest-other"

    private fun previewUser() =
        jwt().authorities(
            SimpleGrantedAuthority(AuthConstants.SCOPE_PREVIEW),
            SimpleGrantedAuthority(AuthConstants.SCOPE_VERIFIED)
        )

    private fun unverifiedUser() = jwt().authorities(SimpleGrantedAuthority(AuthConstants.SCOPE_PREVIEW))

    private fun aiUser() = jwt().authorities(SimpleGrantedAuthority(AuthConstants.SCOPE_AI))

    private fun admin() = jwt().authorities(SimpleGrantedAuthority(AuthConstants.SCOPE_ADMIN))

    @BeforeEach
    fun actAsMe() {
        whenever(owner.id()).thenReturn(me)
        listOf(me, other).forEach {
            quota.forget(it)
            quota.clearLimit(it)
        }
    }

    @Test
    fun `should reject an unauthenticated caller`() {
        mockMvc.perform(get("/agent/quota")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a preview user should read their own weekly quota`() {
        quota.spend(me)

        mockMvc
            .perform(get("/agent/quota").with(previewUser()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.unlimited").value(false))
            .andExpect(jsonPath("$.data.limit").value(10))
            .andExpect(jsonPath("$.data.used").value(1))
            .andExpect(jsonPath("$.data.remaining").value(9))
            .andExpect(jsonPath("$.data.resetAt").isNotEmpty)
            .andExpect(jsonPath("$.data.contact").value("askmonowai@duck.com"))
            .andExpect(jsonPath("$.data.verified").value(true))
    }

    @Test
    fun `a user with an unconfirmed email should read that they are not verified`() {
        mockMvc
            .perform(get("/agent/quota").with(unverifiedUser()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.verified").value(false))
    }

    @Test
    fun `an ai user should read an unlimited quota`() {
        mockMvc
            .perform(get("/agent/quota").with(aiUser()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.unlimited").value(true))
            .andExpect(jsonPath("$.data.verified").value(true))
            .andExpect(jsonPath("$.data.contact").value("askmonowai@duck.com"))
    }

    @Test
    fun `a non-administrator should not read or set another user's limit`() {
        mockMvc.perform(get("/agent/quota/$other").with(previewUser())).andExpect(status().isForbidden)
        mockMvc
            .perform(
                put("/agent/quota/$other")
                    .with(aiUser())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"weeklyLimit":100}""")
            ).andExpect(status().isForbidden)
        mockMvc.perform(delete("/agent/quota/$other").with(aiUser())).andExpect(status().isForbidden)
    }

    @Test
    fun `an administrator should override and then restore a user's limit`() {
        mockMvc
            .perform(
                put("/agent/quota/$other")
                    .with(admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"weeklyLimit":25}""")
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.limit").value(25))
            .andExpect(jsonPath("$.data.override").value(true))

        mockMvc
            .perform(get("/agent/quota/$other").with(admin()))
            .andExpect(jsonPath("$.data.limit").value(25))
            .andExpect(jsonPath("$.data.remaining").value(25))

        mockMvc
            .perform(delete("/agent/quota/$other").with(admin()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.limit").value(10))
            .andExpect(jsonPath("$.data.override").value(false))
    }

    @Test
    fun `an administrator should not set a negative limit`() {
        mockMvc
            .perform(
                put("/agent/quota/$other")
                    .with(admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"weeklyLimit":-1}""")
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `an administrator's request without a limit should be refused, not read as zero`() {
        mockMvc
            .perform(
                put("/agent/quota/$other")
                    .with(admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            ).andExpect(status().isBadRequest)

        mockMvc
            .perform(get("/agent/quota/$other").with(admin()))
            .andExpect(jsonPath("$.data.override").value(false))
    }
}