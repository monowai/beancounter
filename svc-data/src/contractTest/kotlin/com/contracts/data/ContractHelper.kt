package com.contracts.data

import com.beancounter.auth.AuthUtilService
import com.beancounter.common.contracts.RegistrationResponse
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson
import com.beancounter.marketdata.registration.SystemUserService
import org.mockito.Mockito
import org.springframework.core.io.ClassPathResource

/**
 * Authentication helper to resolve 401s with MVC mocking.
 */
class ContractHelper(private val authUtilService: AuthUtilService) {
    companion object {
        @JvmStatic
        fun getSystemUser(): SystemUser {
            val jsonFile = ClassPathResource("contracts/register/register-response.json").file
            val response = BcJson().objectMapper.readValue(jsonFile, RegistrationResponse::class.java)
            return response.data
        }
    }

    fun defaultUser(
        systemUser: SystemUser = getSystemUser(),
        systemUserService: SystemUserService,
    ): SystemUser {
        authUtilService.authenticate(systemUser)

        Mockito.`when`(
            systemUserService
                .find(systemUser.email),
        ).thenReturn(systemUser)
        Mockito.`when`(
            systemUserService
                .getActiveUser(),
        ).thenReturn(systemUser)

        return systemUser
    }
}
