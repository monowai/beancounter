package com.beancounter.client.services

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping

/**
 * Obtain data from Spring Actuators.
 */
@Service
class ActuatorService(
    val actuatorGateway: ActuatorGateway,
) {
    fun ping(): String = actuatorGateway.ping()

    /**
     * Actuators Gateway.
     */
    @FeignClient(
        name = "actuatorGw",
        url = "\${marketdata.actuator:\${marketdata.url}}",
    )
    interface ActuatorGateway {
        @GetMapping(
            value = ["/actuator/health/ping"],
            produces = [MediaType.APPLICATION_JSON_VALUE],
        )
        fun ping(): String
    }
}
