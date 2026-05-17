package com.beancounter.agent.health

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

/**
 * Pings the actuator/health endpoints of each Beancounter service the agent
 * depends on. Returns a structured health response that the chat UI renders
 * as a traffic light.
 *
 * Uses BC_*_ACTUATOR env vars from the shared Helm configmap (bc-common).
 * In local dev, falls back to localhost ports.
 */
@Service
class ServiceHealthChecker(
    @Value($$"${BC_DATA_ACTUATOR:http://localhost:9511}") private val dataActuator: String,
    @Value($$"${BC_POSITION_ACTUATOR:http://localhost:9501}") private val positionActuator: String,
    @Value($$"${BC_EVENT_ACTUATOR:http://localhost:9521}") private val eventActuator: String,
    @Value($$"${BC_RETIRE_ACTUATOR:http://localhost:9541}") private val retireActuator: String,
    @Value($$"${BC_REBALANCE_ACTUATOR:http://localhost:9551}") private val rebalanceActuator: String
) {
    private val log = LoggerFactory.getLogger(ServiceHealthChecker::class.java)

    private val probeClient: RestClient =
        RestClient
            .builder()
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(PROBE_TIMEOUT_MS)
                    setReadTimeout(PROBE_TIMEOUT_MS)
                }
            ).build()

    fun check(llmAvailable: Boolean): AgentHealthResponse {
        val services =
            listOf(
                probe("bc-data", "$dataActuator/actuator/health"),
                probe("bc-position", "$positionActuator/actuator/health"),
                probe("bc-event", "$eventActuator/actuator/health"),
                probe("bc-retire", "$retireActuator/actuator/health"),
                probe("bc-rebalance", "$rebalanceActuator/actuator/health"),
                ServiceStatus(
                    name = "llm",
                    status = if (llmAvailable) "UP" else "DOWN",
                    error = if (llmAvailable) null else "No Spring AI ChatClient configured"
                )
            )

        val up = services.count { it.status == "UP" }
        val overall =
            when (up) {
                services.size -> "GREEN"
                0 -> "RED"
                else -> "AMBER"
            }
        val summary =
            when (overall) {
                "GREEN" -> "All services operational"
                "AMBER" -> "$up of ${services.size} services up"
                else -> "All services unavailable"
            }

        return AgentHealthResponse(
            overallStatus = overall,
            summary = summary,
            services = services
        )
    }

    private fun probe(
        name: String,
        url: String
    ): ServiceStatus =
        try {
            val response =
                probeClient
                    .get()
                    .uri(url)
                    .retrieve()
                    .toBodilessEntity()
            if (response.statusCode.is2xxSuccessful) {
                ServiceStatus(name = name, status = "UP")
            } else {
                ServiceStatus(name = name, status = "DOWN", error = "HTTP ${response.statusCode.value()}")
            }
        } catch (e: Exception) {
            log.debug("Health probe failed for {} at {}: {}", name, url, e.message)
            ServiceStatus(name = name, status = "DOWN", error = e.message ?: e.javaClass.simpleName)
        }

    companion object {
        private const val PROBE_TIMEOUT_MS = 2000
    }
}

data class AgentHealthResponse(
    val overallStatus: String,
    val summary: String,
    val services: List<ServiceStatus>,
    val timestamp: String =
        java.time.Instant
            .now()
            .toString()
)

data class ServiceStatus(
    val name: String,
    val status: String,
    val error: String? = null
)