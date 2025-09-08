plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.sentry)
    alias(libs.plugins.git.properties)
    alias(libs.plugins.download)
    `maven-publish`
}

version = "0.1.1"

publishing {
    publications {
        create<MavenPublication>("agent") {
            artifactId = project.name
            groupId = "org.beancounter"
            version = project.version.toString()
            from(components["java"])
        }
    }
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(platform(libs.spring.cloud.dependencies))
    // Note: Not using Spring AI MCP server, agent is a client
    implementation(platform(libs.otel.bom))
    implementation(project(":jar-common"))
    implementation(project(":jar-client"))
    implementation(project(":jar-auth"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.logging)
    implementation(libs.sentry.openfeign)
    implementation(libs.spring.doc)
    implementation(libs.spring.doc.mvc)
    implementation(libs.spring.boot.starter.aop)
    implementation(libs.spring.cloud.feign) {
        exclude(group = "org.apache.commons", module = "commons-lang3")
        exclude(group = "org.apache.commons", module = "commons-text")
    }
    implementation(libs.spring.boot.starter.security)
    implementation("org.springframework.security:spring-security-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.integration)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation(libs.jackson.kotlin)
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(libs.resilience4j.annotations)
    implementation(libs.resilience4j)
    implementation(libs.spring.kafka)
    // Note: Agent is an MCP client, not an MCP server
    // Note: OpenFeign handles HTTP client internally, no need for explicit HTTP client
    
    compileOnly(libs.spring.boot.configuration.processor)
    
    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.apache.commons", module = "commons-lang3")
        exclude(group = "org.apache.commons", module = "commons-text")
    }
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(libs.spring.kafka.test)
    testImplementation(libs.mockito.kotlin)
    testImplementation(testFixtures(project(":jar-auth")))
}

springBoot {
    buildInfo()
}

gitProperties {
    failOnNoGitDirectory = false
}

tasks.named("bootBuildImage") {
    setProperty("imageName", "monowai/bc-agent")
}

tasks.named("sentryCollectSourcesJava") {
    mustRunAfter("formatKotlin")
}

val isCI = System.getenv("CI")?.toBoolean() ?: false

sentry {
    if (isCI) {
        includeSourceContext = true
        org = "monowai-developments-ltd"
        projectName = "agent"
        authToken = System.getenv("SENTRY_AUTH_TOKEN")
    }
}