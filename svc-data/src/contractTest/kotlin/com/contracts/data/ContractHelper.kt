package com.contracts.data

import com.beancounter.auth.UserUtils
import com.beancounter.common.contracts.RegistrationResponse
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson
import com.beancounter.marketdata.registration.SystemUserRepository
import org.mockito.Mockito
import org.springframework.core.io.ClassPathResource
import java.util.Optional

/**
 * Authentication helper to resolve 401s with MVC mocking.
 */
class ContractHelper(private val userUtils: UserUtils) {

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
        systemUserRepository: SystemUserRepository,
    ): SystemUser {
        userUtils.authUser(systemUser)

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
}
