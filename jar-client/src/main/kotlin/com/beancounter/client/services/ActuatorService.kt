package com.beancounter.client.services

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

/**
 * Obtain data from Spring Actuators.
 */
@Service
class ActuatorService(
    @Qualifier("bcDataRestClient")
    private val restClient: RestClient
) {
    fun ping(): String =
        restClient
            .get()
            .uri("/actuator/health/ping")
            .retrieve()
            .body(String::class.java)
            ?: ""
}