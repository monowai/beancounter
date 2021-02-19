package com.beancounter.common

import com.beancounter.common.contracts.RegistrationRequest
import com.beancounter.common.contracts.RegistrationResponse
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*

class TestRegistration {
    private val objectMapper = BcJson().objectMapper

    @Test
    @Throws(Exception::class)
    fun is_RegistrationSerialization() {
        val registrationRequest = RegistrationRequest("someone@somewhere.com")
        val json = objectMapper.writeValueAsString(registrationRequest)
        assertThat(objectMapper.readValue(json, RegistrationRequest::class.java))
                .usingRecursiveComparison().isEqualTo(registrationRequest)
    }

    @Test
    @Throws(Exception::class)
    fun is_SystemUserSerialization() {
        val systemUser = SystemUser(UUID.randomUUID().toString(), "someone@somewhere.com")
        var json = objectMapper.writeValueAsString(systemUser)
        assertThat(objectMapper.readValue(json, SystemUser::class.java))
                .usingRecursiveComparison().isEqualTo(systemUser)

        val response = RegistrationResponse(systemUser)
        json = objectMapper.writeValueAsString(response)
        assertThat(objectMapper.readValue(json, RegistrationResponse::class.java).data)
                .usingRecursiveComparison().isEqualTo(response.data)
    }
}