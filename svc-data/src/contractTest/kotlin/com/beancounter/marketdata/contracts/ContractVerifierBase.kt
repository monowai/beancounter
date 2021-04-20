package com.beancounter.marketdata.contracts

import com.beancounter.auth.common.TokenService
import com.beancounter.auth.common.TokenUtils
import com.beancounter.common.contracts.RegistrationResponse
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.registration.SystemUserRepository
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.core.io.ClassPathResource
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.web.context.WebApplicationContext
import java.util.Optional

/**
 * Base for contract testing. Mocks commonly used services.
 */
@SpringBootTest(
    classes = [MarketDataBoot::class],
    properties = ["auth.enabled=false"],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@DirtiesContext
@WebAppConfiguration
@ActiveProfiles("contracts")
class ContractVerifierBase {
    internal val objectMapper = BcJson().objectMapper

    internal var dateUtils = DateUtils()

    internal val rateDate = "2019-10-18"

    @MockBean
    internal lateinit var keyGenUtils: KeyGenUtils

    @MockBean
    internal lateinit var jwtDecoder: JwtDecoder

    @MockBean
    internal lateinit var systemUserRepository: SystemUserRepository

    @MockBean
    internal lateinit var tokenService: TokenService

    @Autowired
    internal lateinit var context: WebApplicationContext

    val defaultUser: SystemUser = getSystemUser()

    private final fun getSystemUser(): SystemUser {
        val jsonFile = ClassPathResource("contracts/register/register-response.json").file
        val response = objectMapper.readValue(jsonFile, RegistrationResponse::class.java)
        return response.data
    }

    fun defaultUser(
        systemUser: SystemUser = defaultUser,
    ): SystemUser {
        Mockito.`when`(jwtDecoder.decode(systemUser.email))
            .thenReturn(TokenUtils().getUserToken(systemUser))

        Mockito.`when`(
            systemUserRepository
                .findById(systemUser.email)
        ).thenReturn(Optional.of(defaultUser))

        SecurityContextHolder.getContext().authentication =
            JwtAuthenticationToken(
                jwtDecoder.decode(
                    systemUser.email
                )
            )

        Mockito.`when`(tokenService.subject)
            .thenReturn(systemUser.email)
        return systemUser
    }
}
