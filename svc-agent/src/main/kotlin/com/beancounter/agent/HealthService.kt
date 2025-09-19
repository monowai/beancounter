package com.beancounter.agent

import com.beancounter.agent.client.DataMcpClient
import com.beancounter.agent.client.EventMcpClient
import com.beancounter.agent.client.PositionMcpClient
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.ForbiddenException
import com.beancounter.common.exception.SystemException
import com.beancounter.common.exception.UnauthorizedException
import feign.FeignException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.IOException
import java.time.LocalDateTime

/**
 * Service to check the health status of all MCP services
 */
@Service
class HealthService(
    private val dataMcpClient: DataMcpClient,
    private val eventMcpClient: EventMcpClient,
    private val positionMcpClient: PositionMcpClient
) {
    private val log = LoggerFactory.getLogger(HealthService::class.java)

    companion object {
        private const val CONNECTION_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 5000
    }

    /**
     * Check the health status of all MCP services
     */
    fun checkAllServicesHealth(): ServiceHealthStatus {
        val services =
            listOf(
                ServiceInfo("Data Service", "data", "http://localhost:9510", dataMcpClient),
                ServiceInfo("Event Service", "event", "http://localhost:9520", eventMcpClient),
                ServiceInfo("Position Service", "position", "http://localhost:9500", positionMcpClient)
            )

        val serviceStatuses =
            services.map { serviceInfo ->
                checkServiceHealthDetailed(serviceInfo)
            }

        val upServices = serviceStatuses.count { it.status == "UP" }
        val amberServices = serviceStatuses.count { it.status == "AMBER" }
        val downServices = serviceStatuses.count { it.status == "DOWN" }
        val totalServices = serviceStatuses.size

        val overallStatus =
            when {
                upServices == totalServices -> "GREEN"
                upServices > 0 || amberServices > 0 -> "AMBER"
                else -> "RED"
            }

        val summary =
            when {
                upServices == totalServices -> "All services operational"
                downServices == totalServices -> "All services down"
                else -> "$upServices operational, $amberServices partial, $downServices down"
            }

        return ServiceHealthStatus(
            overallStatus = overallStatus,
            services = serviceStatuses,
            lastChecked = LocalDateTime.now(),
            summary = summary
        )
    }

    /**
     * Check health of a service with detailed status (server vs MCP service)
     */
    private fun checkServiceHealthDetailed(serviceInfo: ServiceInfo): ServiceStatus {
        val startTime = System.currentTimeMillis()

        // First, check if the server is reachable via actuator
        val serverReachable = isServerReachable(serviceInfo)

        if (!serverReachable) {
            return ServiceStatus(
                name = serviceInfo.name,
                status = "DOWN",
                responseTime = System.currentTimeMillis() - startTime,
                lastChecked = LocalDateTime.now(),
                error = "Server unreachable"
            )
        }

        // Server is reachable, now check MCP service functionality using ping endpoints
        return checkMcpServiceHealth(serviceInfo, startTime)
    }

    private fun isServerReachable(serviceInfo: ServiceInfo): Boolean =
        try {
            val actuatorUrl = "${serviceInfo.baseUrl}/actuator/health"
            val response = java.net.URL(actuatorUrl).openConnection()
            response.connectTimeout = CONNECTION_TIMEOUT_MS
            response.readTimeout = READ_TIMEOUT_MS
            response.connect()
            true
        } catch (e: IOException) {
            log.trace("Server {} not reachable: {}", serviceInfo.name, e.message)
            false
        }

    private fun checkMcpServiceHealth(
        serviceInfo: ServiceInfo,
        startTime: Long
    ): ServiceStatus =
        try {
            when (serviceInfo.name) {
                "Data Service" -> {
                    // Try the ping endpoint first (no auth required)
                    val pingResponse = dataMcpClient.ping()
                    log.trace("Data service ping response: {}", pingResponse)
                    ServiceStatus(
                        name = serviceInfo.name,
                        status = "UP",
                        responseTime = System.currentTimeMillis() - startTime,
                        lastChecked = LocalDateTime.now(),
                        error = null
                    )
                }
                "Event Service" -> {
                    // Try the ping endpoint first (no auth required)
                    val pingResponse = eventMcpClient.ping()
                    log.trace("Event service ping response: {}", pingResponse)
                    ServiceStatus(
                        name = serviceInfo.name,
                        status = "UP",
                        responseTime = System.currentTimeMillis() - startTime,
                        lastChecked = LocalDateTime.now(),
                        error = null
                    )
                }
                "Position Service" -> {
                    // Try the ping endpoint first (no auth required)
                    val pingResponse = positionMcpClient.ping()
                    log.trace("Position service ping response: {}", pingResponse)
                    ServiceStatus(
                        name = serviceInfo.name,
                        status = "UP",
                        responseTime = System.currentTimeMillis() - startTime,
                        lastChecked = LocalDateTime.now(),
                        error = null
                    )
                }
                else ->
                    ServiceStatus(
                        name = serviceInfo.name,
                        status = "UNKNOWN",
                        responseTime = System.currentTimeMillis() - startTime,
                        lastChecked = LocalDateTime.now(),
                        error = "Unknown service"
                    )
            }
        } catch (e: Exception) {
            val (status, errorMessage) =
                when (e) {
                    is UnauthorizedException -> {
                        log.warn("MCP service {} returned 401 Unauthorized: {}", serviceInfo.name, e.message)
                        "DOWN" to "401 Unauthorized: ${e.message}"
                    }
                    is ForbiddenException -> {
                        log.warn("MCP service {} returned 403 Forbidden: {}", serviceInfo.name, e.message)
                        "DOWN" to "403 Forbidden: ${e.message}"
                    }
                    is BusinessException -> {
                        log.warn("MCP service {} returned 4xx error: {}", serviceInfo.name, e.message)
                        "AMBER" to "4xx Client Error: ${e.message}"
                    }
                    is SystemException -> {
                        log.warn("MCP service {} returned 5xx error: {}", serviceInfo.name, e.message)
                        "DOWN" to "5xx Server Error: ${e.message}"
                    }
                    is FeignException -> {
                        log.warn("MCP service {} returned Feign error: {}", serviceInfo.name, e.message)
                        "DOWN" to "Feign Error (${e.status()}): ${e.message}"
                    }
                    else -> {
                        log.warn("MCP service {} failed with unexpected error: {}", serviceInfo.name, e.message)
                        "DOWN" to "Unexpected Error: ${e.message}"
                    }
                }

            ServiceStatus(
                name = serviceInfo.name,
                status = status,
                responseTime = System.currentTimeMillis() - startTime,
                lastChecked = LocalDateTime.now(),
                error = errorMessage
            )
        }
}

/**
 * Information about a service
 */
data class ServiceInfo(
    val name: String,
    val key: String,
    val baseUrl: String,
    val client: Any
)

/**
 * Status of a single service
 */
data class ServiceStatus(
    val name: String,
    val status: String, // UP, AMBER, DOWN, UNKNOWN
    val responseTime: Long,
    val lastChecked: LocalDateTime,
    val error: String?
)

/**
 * Overall health status of all services
 */
data class ServiceHealthStatus(
    val overallStatus: String, // GREEN, AMBER, RED
    val services: List<ServiceStatus>,
    val lastChecked: LocalDateTime,
    val summary: String
)