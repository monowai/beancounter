package com.beancounter.auth

import com.beancounter.auth.model.AuthConstants
import com.beancounter.auth.server.WebAuthFilterConfig
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cache.CacheManager
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestOperations

/**
 * MVC Auth controller tests for OAuth.
 */
@ExtendWith(SpringExtension::class)
@EnableWebSecurity
@WebMvcTest(
    AuthTest.SimpleController::class,
    properties = ["auth.enabled=true"],
)
@ContextConfiguration(
    classes = [
        AuthTest.SimpleController::class,
        WebAuthFilterConfig::class,
        DefaultJWTProcessor::class,
    ],
)
class AuthTest {

    @MockBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockBean
    private lateinit var cacheManager: CacheManager

    @MockBean
    private lateinit var jwtRestOperations: RestOperations

    @MockBean
    private lateinit var authConfig: AuthConfig

    @Autowired
    private lateinit var mockMvc: MockMvc

    companion object {
        private const val hello = "/api/hello"
        const val what = "/api/what"
    }

    @Test
    fun has_NoTokenAndIsUnauthorized() {
        var result = mockMvc.perform(
            MockMvcRequestBuilders.get(hello)
                .contentType(MediaType.APPLICATION_JSON),
        ).andExpect(MockMvcResultMatchers.status().isUnauthorized)
            .andReturn()
        assertThat(result.response.status).isEqualTo(HttpStatus.UNAUTHORIZED.value())
        result = mockMvc.perform(
            MockMvcRequestBuilders.get(what)
                .contentType(MediaType.APPLICATION_JSON),
        ).andExpect(MockMvcResultMatchers.status().isUnauthorized)
            .andReturn()
        assertThat(result.response.status).isEqualTo(HttpStatus.UNAUTHORIZED.value())
    }

    @Test
    @WithMockUser(username = "testUser", authorities = [AuthConstants.SCOPE_BC, AuthConstants.SCOPE_USER])
    fun has_AuthorityToSayHello() {
        mockMvc.perform(MockMvcRequestBuilders.get(hello))
            .andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn()
    }

    @Test
    @WithMockUser(username = "testUser", authorities = [AuthConstants.SCOPE_USER])
    fun has_NoAuthorityToSayWhat() {
        mockMvc.perform(MockMvcRequestBuilders.get(what))
            .andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isForbidden)
            .andReturn()
    }

    @Test
    @Throws(Exception::class)
    @WithMockUser(username = "testUser", authorities = ["no-valid-auth"])
    fun has_tokenButNoRoleToSayAnything() {
        mockMvc.perform(
            MockMvcRequestBuilders.get(hello)
                .contentType(MediaType.APPLICATION_JSON),
        ).andExpect(MockMvcResultMatchers.status().isForbidden)
            .andReturn()

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get(what)
                .contentType(MediaType.APPLICATION_JSON),
        ).andExpect(MockMvcResultMatchers.status().isForbidden)
            .andReturn()
        assertThat(result.response.status).isEqualTo(HttpStatus.FORBIDDEN.value())
    }

    @RestController
    internal class SimpleController {

        @GetMapping(hello)
        @PreAuthorize("hasAuthority('${AuthConstants.SCOPE_USER}')")
        fun sayHello(): String {
            return "hello"
        }

        @GetMapping(what)
        @PreAuthorize("hasAuthority('${AuthConstants.ADMIN}')")
        fun sayWhat(): String {
            return "no one can call this"
        }
    }
}
