package com.contracts.data

import com.beancounter.auth.AutoConfigureNoAuth
import com.beancounter.auth.TokenService
import com.beancounter.auth.UserUtils
import com.beancounter.marketdata.Constants.Companion.systemUser
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.registration.SystemUserRepository
import io.restassured.RestAssured
import io.restassured.authentication.OAuthScheme
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import java.util.Optional

/**
 * Base class for Authenticated registration contract tests. This is called by the spring cloud contract verifier
 */
@SpringBootTest(
    classes = [MarketDataBoot::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@ActiveProfiles("contracts")
@AutoConfigureNoAuth
@WithMockUser("blah@blah.com", roles = ["beancounter", "beancounter:user"])
class RegisterBase {
    private val notAuthenticated = "not@authenticated.com"

    @LocalServerPort
    lateinit var port: String

    @MockBean
    lateinit var tokenService: TokenService

    @MockBean
    lateinit var jwtDecoder: JwtDecoder

    @MockBean
    lateinit var systemUserRepository: SystemUserRepository

    @Autowired
    lateinit var userUtils: UserUtils

    @BeforeEach
    fun mockRegistration() {
        RestAssured.port = Integer.valueOf(port)
        RestAssured.authentication = OAuthScheme()
        Mockito.`when`(systemUserRepository.findById(notAuthenticated))
            .thenReturn(Optional.ofNullable(null))

        Mockito.`when`(systemUserRepository.findByAuth0(notAuthenticated))
            .thenReturn(Optional.ofNullable(null))
        Mockito.`when`(tokenService.subject)
            .thenReturn(systemUser.email)

        ContractHelper(userUtils).defaultUser(
            systemUserRepository = systemUserRepository,
        )
    }
}
