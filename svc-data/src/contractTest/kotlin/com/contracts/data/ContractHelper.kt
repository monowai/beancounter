package com.contracts.data

import com.beancounter.auth.AuthConfig
import com.beancounter.auth.NoAuthConfig
import com.beancounter.auth.TokenUtils
import com.beancounter.common.contracts.RegistrationResponse
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson
import com.beancounter.marketdata.registration.SystemUserRepository
import org.mockito.Mockito
import org.springframework.core.io.ClassPathResource
import org.springframework.security.core.context.SecurityContextHolder
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
            noAuthConfig: NoAuthConfig,
            systemUserRepository: SystemUserRepository,
        ): SystemUser {
            authUser(systemUser, noAuthConfig)

            Mockito.`when`(
                systemUserRepository
                    .findById(systemUser.email),
            ).thenReturn(Optional.of(systemUser))
            Mockito.`when`(
                systemUserRepository
                    .findByAuth0(systemUser.email),
            ).thenReturn(Optional.of(systemUser))

            return systemUser
        }

        fun authUser(
            systemUser: SystemUser,
            noAuthConfig: NoAuthConfig,

        ) {
            val authConfig = AuthConfig()
            authConfig.claimEmail = "email"
            Mockito.`when`(noAuthConfig.jwtDecoder.decode(systemUser.email))
                .thenReturn(TokenUtils(authConfig).getUserToken(systemUser))

            SecurityContextHolder.getContext().authentication =
                JwtAuthenticationToken(
                    noAuthConfig.jwtDecoder.decode(
                        systemUser.email,
                    ),
                )

            Mockito.`when`(noAuthConfig.tokenService.subject)
                .thenReturn(systemUser.email)
        }
    }
}
