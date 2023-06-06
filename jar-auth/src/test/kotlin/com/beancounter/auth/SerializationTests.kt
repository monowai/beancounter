package com.beancounter.auth

import com.beancounter.auth.client.LoginService
import com.beancounter.common.utils.BcJson
import feign.form.util.PojoUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Are POJOs serializing via JACKSON and Spring's Pojo->multiPartForm encoding approach.
 */
class SerializationTests {
    private val oidClientId = "client_id"
    private val oidSecret = "client_secret"
    private val oidGrantType = "grant_type"
    private val clientId = "clientId"
    private val grantType = "grantType"
    private val secret = "*secretx*"

    @Test
    fun is_MachineToMachineJsonCorrect() {
        val machineRequest =
            LoginService.MachineRequest(clientId = "abc", clientSecret = secret, audience = "my-audience")
        assertThat(machineRequest.grantType).isNotNull
        val json = BcJson().objectMapper.writeValueAsString(machineRequest)
        assertThat(json).contains(
            oidClientId,
            oidSecret,
            oidGrantType,
            "abc",
            secret,
            "client_credentials",
        )
        val translateResult = PojoUtil.toMap(machineRequest)
        assertThat(translateResult)
            .containsKeys(clientId, "clientSecret", grantType)
    }

    @Test
    fun is_InteractiveJsonCorrect() {
        val loginRequest = LoginService.LoginRequest(clientId = "cid", username = "mike", password = secret)
        assertThat(loginRequest.grantType).isNotNull
        val json = BcJson().objectMapper.writeValueAsString(loginRequest)
        assertThat(json).contains(
            oidClientId,
            oidGrantType,
            "cid",
            "username",
            "password",
            secret,
        )
        val translateResult = PojoUtil.toMap(loginRequest)
        assertThat(translateResult)
            .containsKeys(clientId, grantType)
    }
}
