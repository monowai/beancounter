package com.beancounter.auth

import com.beancounter.auth.client.LoginService
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Test suite for authentication POJOs to ensure proper serialization via Jackson and Spring's form encoding.
 *
 * This class tests:
 * - Machine-to-machine (M2M) authentication request serialization
 * - Interactive user authentication request serialization
 * - JSON serialization and deserialization round trips
 * - Form encoding compatibility for OAuth flows
 *
 * Tests verify that authentication objects can be properly serialized for OAuth
 * provider communication and form-based authentication flows.
 */
class SerializationTests {
    private val oidClientId = "client_id"
    private val oidSecret = "client_secret"
    private val oidGrantType = "grant_type"
    private val secret = "*secretx*"

    @Test
    fun `should serialize machine-to-machine authentication request correctly`() {
        val clientCredentialsRequest =
            LoginService.ClientCredentialsRequest(
                client_id = "abc",
                client_secret = secret,
                audience = "my-audience"
            )
        assertThat(clientCredentialsRequest.grant_type).isNotNull
        TestHelpers.assertSerializationRoundTrip(clientCredentialsRequest)

        val json = objectMapper.writeValueAsString(clientCredentialsRequest)
        assertThat(json).contains(
            oidClientId,
            oidSecret,
            oidGrantType,
            "abc",
            secret,
            "client_credentials"
        )
        val fromJson: Map<String, String> = objectMapper.readValue(json)
        assertThat(fromJson)
            .containsKeys(
                oidClientId,
                oidSecret,
                oidGrantType
            )
    }

    @Test
    fun `should serialize interactive authentication request correctly`() {
        val passwordRequest =
            LoginService.PasswordRequest(
                client_id = "cid",
                username = "mike",
                password = secret,
                audience = "the-audience",
                client_secret = "the-secret"
            )
        assertThat(passwordRequest.grant_type).isNotNull
        TestHelpers.assertSerializationRoundTrip(passwordRequest)

        val json = objectMapper.writeValueAsString(passwordRequest)
        assertThat(json).contains(
            oidClientId,
            oidGrantType,
            "cid",
            "username",
            "password",
            "the-secret",
            "scope",
            secret
        )
        val fromJson: Map<String, String> = objectMapper.readValue(json)
        assertThat(fromJson)
            .isNotEmpty
            .containsKeys(
                oidClientId,
                oidGrantType
            )
    }
}