package com.contracts.data

import com.beancounter.auth.AutoConfigureNoAuth
import com.beancounter.auth.NoWebAuth
import com.beancounter.auth.TokenService
import com.beancounter.auth.UserUtils
import com.beancounter.common.contracts.RegistrationResponse
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.registration.SystemUserService
import io.restassured.RestAssured
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles

/**
 * Base class for Authenticated registration contract tests. This is called by the spring cloud contract verifier
 */
@SpringBootTest(
    classes = [MarketDataBoot::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@ActiveProfiles("contracts")
@AutoConfigureNoAuth
class RegisterBase {

    @LocalServerPort
    lateinit var port: String

    @MockBean
    internal lateinit var jwtDecoder: JwtDecoder

    @MockBean
    internal lateinit var tokenService: TokenService

    @MockBean
    lateinit var systemUserService: SystemUserService

    @Autowired
    lateinit var noWebAuth: NoWebAuth

    @Autowired
    lateinit var userUtils: UserUtils

    internal val systemUser = ContractHelper.getSystemUser()

    @BeforeEach
    fun mockRegistration() {
        RestAssured.port = Integer.valueOf(port)
        val jwt = userUtils.authenticate(systemUser)
        Mockito.`when`(systemUserService.register())
            .thenReturn(RegistrationResponse(systemUser))
        Mockito.`when`(tokenService.subject).thenReturn(jwt.token.subject)
        ContractHelper(userUtils).defaultUser(
            systemUser,
            systemUserService = systemUserService,
        )
    }
}
