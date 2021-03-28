@file:Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")

package com.beancounter.auth

import com.beancounter.auth.AuthTest.SimpleController
import com.beancounter.auth.common.TokenService
import com.beancounter.auth.common.TokenUtils
import com.beancounter.auth.server.AuthorityRoleConverter
import com.beancounter.auth.server.JwtRoleConverter
import com.beancounter.auth.server.ResourceServerConfig
import com.beancounter.auth.server.RoleHelper
import com.beancounter.common.model.SystemUser
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockServletContext
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.WebApplicationContext

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [MockServletContext::class, TokenService::class, SimpleController::class, NimbusJwtDecoder::class, DefaultJWTProcessor::class, ResourceServerConfig::class])
@ImportAutoConfiguration(
    WebMvcAutoConfiguration::class
)
@WebAppConfiguration
class AuthTest {
    private val roleConverter = AuthorityRoleConverter()

    private val tokenUtils: TokenUtils = TokenUtils()

    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var tokenService: TokenService

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setupMockMvc() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(context)
            .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
            .build()
    }

    @Test
    fun is_BearerToken() {
        assertThat(tokenService.getBearerToken("Test")).isEqualTo("Bearer Test")
    }

    @Test
    fun are_DefaultGrantsConvertedFromToken() {
        val jwtRoleConverter = JwtRoleConverter()
        val user = SystemUser("user")
        val token = tokenUtils.getUserToken(user)
        val converted = jwtRoleConverter.convert(token)
        assertThat(converted).isNotNull
        val defaultGrants = converted.authorities
        assertThat(defaultGrants)
            .contains(SimpleGrantedAuthority(RoleHelper.ROLE_USER))
            .contains(SimpleGrantedAuthority(RoleHelper.SCOPE_BC))
    }

    @Test
    @Throws(Exception::class)
    fun has_NoTokenAndIsUnauthorized() {
        var result = mockMvc.perform(
            MockMvcRequestBuilders.get("/hello")
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isUnauthorized)
            .andReturn()
        assertThat(result.response.status).isEqualTo(HttpStatus.UNAUTHORIZED.value())
        result = mockMvc.perform(
            MockMvcRequestBuilders.get("/what")
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isUnauthorized)
            .andReturn()
        assertThat(result.response.status).isEqualTo(HttpStatus.UNAUTHORIZED.value())
    }

    @Test
    @Throws(Exception::class)
    fun has_AuthorityToSayHelloButNotToSayWhat() {
        val user = SystemUser("user")
        val token = tokenUtils.getUserToken(user)
        mockMvc.perform(
            MockMvcRequestBuilders.get("/hello")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(roleConverter))
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn()
        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/what")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(roleConverter)
                ).contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isForbidden)
            .andReturn()
        assertThat(result.response.status).isEqualTo(HttpStatus.FORBIDDEN.value())
    }

    @Test
    @Throws(Exception::class)
    fun has_tokenButNoRoleToSayAnything() {
        val user = SystemUser(id = "user")
        val token = tokenUtils.getUserToken(user, tokenUtils.getRoles("blah"))
        var result = mockMvc.perform(
            MockMvcRequestBuilders.get("/hello")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(roleConverter)
                )
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isForbidden)
            .andReturn()
        assertThat(result.response.status).isEqualTo(HttpStatus.FORBIDDEN.value())
        result = mockMvc.perform(
            MockMvcRequestBuilders.get("/what")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(roleConverter)
                )
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isForbidden)
            .andReturn()
        assertThat(result.response.status).isEqualTo(HttpStatus.FORBIDDEN.value())
    }

    @Test
    @Throws(Exception::class)
    fun has_NoIdentityCrisis() {
        val user = SystemUser("user")
        val token = tokenUtils.getUserToken(user)
        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/me")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(roleConverter))
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn()
        assertThat(result.response.contentAsString)
            .isEqualTo(user.id)
    }

    @Test
    fun is_BearerTokenBearing() {
        assertThat(tokenService.bearerToken)
            .isEqualTo(TokenService.BEARER + tokenService.token)
    }

    @RestController
    internal class SimpleController {
        @Autowired
        private lateinit var tokenService: TokenService

        @GetMapping("/hello")
        @PreAuthorize("hasRole('" + RoleHelper.OAUTH_USER + "')")
        fun sayHello(): String {
            return "hello"
        }

        @GetMapping("/me")
        @PreAuthorize("hasRole('" + RoleHelper.OAUTH_USER + "')")
        fun me(): String? {
            assert(tokenService.jwtToken != null)
            return tokenService.jwtToken!!.name
        }

        @GetMapping("/what")
        @PreAuthorize("hasRole('no-one')")
        fun sayWhat(): String {
            return "no one can call this"
        }
    }
}
