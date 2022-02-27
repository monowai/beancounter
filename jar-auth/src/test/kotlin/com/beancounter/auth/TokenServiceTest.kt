package com.beancounter.auth

import com.beancounter.auth.client.LoginService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean

@SpringBootTest(classes = [TokenService::class])
class TokenServiceTest {
    @Autowired
    private lateinit var tokenService: TokenService

    @MockBean
    private lateinit var loginService: LoginService

    @MockBean
    private lateinit var authConfig: AuthConfig

    @Test
    fun is_BearerToken() {
        assertThat(tokenService.getBearerToken("Test")).isEqualTo("Bearer Test")
    }

    @Test
    fun is_BearerTokenBearing() {
        assertThat(tokenService.bearerToken)
            .isEqualTo(TokenService.BEARER + tokenService.token)
    }
}
