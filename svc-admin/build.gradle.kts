plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.git.properties)
}

version = "0.1.1"

dependencies {
    implementation(platform(libs.spring.boot.dependencies))

    // Spring Boot Admin server — UI + REST endpoints for monitoring registered actuator clients.
    // 3.5.x track aligns with Spring Boot ${libs.versions.spring.boot.get()}.
    implementation("de.codecentric:spring-boot-admin-starter-server:3.5.4")

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.logging)

    // OAuth2 client for SBA UI login + outbound bearer-token attachment to client actuators.
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

    implementation(libs.jackson.kotlin)

    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.apache.commons", module = "commons-lang3")
        exclude(group = "org.apache.commons", module = "commons-text")
    }
    testImplementation(libs.mockito.kotlin)
}

springBoot {
    buildInfo {
        properties {
            additional.set(
                mapOf(
                    "ci.buildNumber" to (System.getenv("CIRCLE_BUILD_NUM") ?: "local")
                )
            )
        }
    }
}

gitProperties {
    failOnNoGitDirectory = false
}

tasks.named("bootBuildImage") {
    setProperty("imageName", "monowai/bc-admin")
}
