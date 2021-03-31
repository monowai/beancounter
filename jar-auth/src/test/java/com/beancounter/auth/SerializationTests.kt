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
    private val clientId = "client_id"
    private val grantType = "grant_type"

    @Test
    fun is_MachineToMachineJsonCorrect() {
        val machineRequest = LoginService.MachineRequest(client_id = "abc", client_secret = "*secret*")
        assertThat(machineRequest.grant_type).isNotNull
        val json = BcJson().objectMapper.writeValueAsString(machineRequest)
        assertThat(json).contains(
            clientId,
            "abc",
            "*secret*",
            "client_secret",
            grantType,
            "client_credentials"
        )
        val translateResult = PojoUtil.toMap(machineRequest)
        assertThat(translateResult).containsKeys(clientId, "client_secret", grantType)
    }

    @Test
    fun is_InteractiveJsonCorrect() {
        val loginRequest = LoginService.LoginRequest(client_id = "cid", username = "mike", password = "*secretx*")
        assertThat(loginRequest.grant_type).isNotNull
        val json = BcJson().objectMapper.writeValueAsString(loginRequest)
        assertThat(json).contains(
            clientId,
            "cid",
            "username",
            "password",
            "*secretx*",
            grantType,
        )
        val translateResult = PojoUtil.toMap(loginRequest)
        assertThat(translateResult).containsKeys("password", "username", grantType)
    }
}
