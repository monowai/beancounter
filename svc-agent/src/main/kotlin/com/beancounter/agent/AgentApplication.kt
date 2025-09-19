package com.beancounter.agent

import jakarta.annotation.PostConstruct
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * Beancounter AI Agent Application
 *
 * This service provides an AI agent that can communicate with the MCP servers
 * (Data, Event, Position) and support LLM capabilities for natural language
 * portfolio and market analysis.
 */
@SpringBootApplication(
    scanBasePackages = ["com.beancounter"],
    exclude = [
        org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration::class,
        org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration::class
    ]
)
class AgentApplication

/**
 * Configuration to enable SecurityContext propagation to child threads.
 * This ensures JWT tokens are properly forwarded from Spring MVC request threads
 * to Feign client threads when making MCP service calls.
 */
@Component
class SecurityContextConfiguration {
    @PostConstruct
    fun configureSecurityContext() {
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL)
    }
}

fun main(args: Array<String>) {
    runApplication<AgentApplication>(args = args)
}