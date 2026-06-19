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
@SpringBootApplication(
    scanBasePackages = ["com.beancounter"],
    // Boot 4 relocated these auto-configs to dedicated modules
    // (spring-boot-jdbc / spring-boot-hibernate) that are NOT on svc-agent's
    // classpath. Exclude by name so the defensive guard survives even though
    // the classes can't be referenced as ::class literals here.
    excludeName = [
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
    ]
)
class AgentApplication

fun main(args: Array<String>) {
    runApplication<AgentApplication>(args = args)
}