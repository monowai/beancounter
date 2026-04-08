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
 * The downstream services expose management endpoints on dedicated ports
 * (9511/9501/9521 in local dev) with an unauthenticated `/actuator/health`
 * endpoint, so this probe can run on page load before the user has pasted a
 * bearer token.
 */
@Service
class ServiceHealthChecker(
    @Value($$"${marketdata.actuator.url:http://localhost:9511/actuator/health}") private val dataHealth: String,
    @Value($$"${position.actuator.url:http://localhost:9501/actuator/health}") private val positionHealth: String,
    @Value($$"${event.actuator.url:http://localhost:9521/actuator/health}") private val eventHealth: String,
    @Value($$"${retire.actuator.url:http://localhost:9541/actuator/health}") private val retireHealth: String,
    @Value($$"${rebalance.actuator.url:http://localhost:9551/actuator/health}") private val rebalanceHealth: String
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
                probe("bc-data", dataHealth),
                probe("bc-position", positionHealth),
                probe("bc-event", eventHealth),
                probe("bc-retire", retireHealth),
                probe("bc-rebalance", rebalanceHealth),
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