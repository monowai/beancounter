package com.beancounter.agent

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Beancounter AI Agent.
 *
 * Exposes a natural-language query endpoint backed by Spring AI's [@Tool] function
 * calling. Tool methods invoke the standard Beancounter REST APIs (svc-data via
 * jar-client, svc-position and svc-event via local thin clients) — no custom
 * protocol layer.
 */
@SpringBootApplication(scanBasePackages = ["com.beancounter"])
class AgentApplication

fun main(args: Array<String>) {
    runApplication<AgentApplication>(args = args)
}