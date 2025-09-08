package com.beancounter.agent

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

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

fun main(args: Array<String>) {
    runApplication<AgentApplication>(args = args)
}