package com.contracts.data

import com.beancounter.auth.AuthConfig
import com.beancounter.auth.TokenService
import com.beancounter.auth.TokenUtils
import com.beancounter.common.contracts.RegistrationResponse
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson
import com.beancounter.marketdata.registration.SystemUserRepository
import org.mockito.Mockito
import org.springframework.core.io.ClassPathResource
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.util.Optional

/**
 * Authentication helper to resolve 401s with MVC mocking.
 */
class ContractHelper {
    companion object {
        @JvmStatic
        fun getSystemUser(): SystemUser {
            val jsonFile = ClassPathResource("contracts/register/register-response.json").file
            val response = BcJson().objectMapper.readValue(jsonFile, RegistrationResponse::class.java)
            return response.data
        }

        @JvmStatic
        fun defaultUser(
            systemUser: SystemUser = getSystemUser(),
            jwtDecoder: JwtDecoder,
            tokenService: TokenService,
            systemUserRepository: SystemUserRepository
        ): SystemUser {
            val authConfig = AuthConfig()
            authConfig.claimEmail = "email"
            Mockito.`when`(jwtDecoder.decode(systemUser.email))
                .thenReturn(TokenUtils(authConfig).getUserToken(systemUser))

            Mockito.`when`(
                systemUserRepository
                    .findById(systemUser.email)
            ).thenReturn(Optional.of(systemUser))

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
}
