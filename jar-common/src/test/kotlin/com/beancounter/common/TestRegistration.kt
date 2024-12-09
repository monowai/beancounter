package com.beancounter.common

import com.beancounter.common.contracts.RegistrationRequest
import com.beancounter.common.contracts.RegistrationResponse
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Serialization of Registration payloads
 */
class TestRegistration {
    @Test
    fun registrationSerializes() {
        val registrationRequest = RegistrationRequest(false)
        val json = objectMapper.writeValueAsString(registrationRequest)
        assertThat(
            objectMapper.readValue(
                json,
                RegistrationRequest::class.java
            )
        ).usingRecursiveComparison()
            .isEqualTo(registrationRequest)
    }

    @Test
    fun systemUserSerializes() {
        val systemUser =
            SystemUser(
                UUID.randomUUID().toString(),
                "no-one@nowhere.com"
            )
        var json = objectMapper.writeValueAsString(systemUser)
        assertThat(
            objectMapper.readValue(
                json,
                SystemUser::class.java
            )
        ).usingRecursiveComparison()
            .isEqualTo(systemUser)

        val response = RegistrationResponse(systemUser)
        json = objectMapper.writeValueAsString(response)
        assertThat(
            objectMapper
                .readValue(
                    json,
                    RegistrationResponse::class.java
                ).data
        ).usingRecursiveComparison()
            .isEqualTo(response.data)
    }
}