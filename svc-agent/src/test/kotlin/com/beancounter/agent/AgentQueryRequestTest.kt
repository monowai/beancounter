package com.beancounter.agent

import com.beancounter.agent.conversation.ConversationOwner
import com.beancounter.agent.conversation.ConversationTitler
import com.beancounter.auth.model.AuthConstants
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * The `/agent/query` request contract over HTTP: a caller may leave the
 * question out when the page has a canned one, and is told it sent a bad
 * request when the page has none. Configured as ConversationControllerTest
 * so the two share one Spring context.
 */
@SpringBootTest(
    properties = [
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://beancounter.eu.auth0.com/",
        "auth.audience=https://holdsworth.app"
    ]
)
@AutoConfigureMockMvc
class AgentQueryRequestTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var owner: ConversationOwner

    @MockitoBean
    private lateinit var titler: ConversationTitler

    private fun ask(body: String) =
        mockMvc.perform(
            post("/agent/query")
                .with(jwt().authorities(SimpleGrantedAuthority(AuthConstants.SCOPE_AI)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )

    @Test
    fun `should reject a request with no question on a page that has no canned question`() {
        ask("""{"context":{"page":"Settings"}}""").andExpect(status().isBadRequest)
    }

    @Test
    fun `should reject a blank question on a page that has no canned question`() {
        ask("""{"query":"  ","context":{"page":"Settings"}}""").andExpect(status().isBadRequest)
    }
}