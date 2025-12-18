package com.contracts.data

import com.beancounter.auth.AuthUtilService
import com.beancounter.auth.AutoConfigureNoAuth
import com.beancounter.auth.TokenService
import com.beancounter.common.contracts.RegistrationResponse
import com.beancounter.common.model.UserPreferences
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.registration.SystemUserService
import com.beancounter.marketdata.registration.UserPreferencesService
import io.restassured.RestAssured
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Base class for Authenticated registration contract tests. This is called by the spring cloud contract verifier
 */
@SpringBootTest(
    classes = [MarketDataBoot::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("contracts")
@AutoConfigureNoAuth
class RegisterBase {
    @LocalServerPort
    lateinit var port: String

    @MockitoBean
    internal lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    internal lateinit var tokenService: TokenService

    @MockitoBean
    lateinit var systemUserService: SystemUserService

    @MockitoBean
    lateinit var userPreferencesService: UserPreferencesService

    @Autowired
    lateinit var authUtilService: AuthUtilService

    internal val systemUser = ContractHelper.getSystemUser()

    @BeforeEach
    fun mockRegistration() {
        RestAssured.port = Integer.valueOf(port)
        val jwt = authUtilService.authenticate(systemUser)
        val userPreferences = UserPreferences(owner = systemUser)
        Mockito
            .`when`(systemUserService.register())
            .thenReturn(RegistrationResponse(systemUser))
        Mockito
            .`when`(userPreferencesService.getOrCreate(systemUser))
            .thenReturn(userPreferences)
        Mockito.`when`(tokenService.subject).thenReturn(jwt.token.subject)
        ContractHelper(authUtilService).defaultUser(
            systemUser,
            systemUserService = systemUserService
        )
    }
}