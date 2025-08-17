plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.java.test.fixtures)
    alias(libs.plugins.sentry)
    alias(libs.plugins.git.properties)
    alias(libs.plugins.download)
    `maven-publish`
}

version = "0.1.1"

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.4"))
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies:2025.0.0"))
    implementation(project(":jar-common"))
    implementation(project(":jar-auth"))
    implementation(project(":jar-client"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-logging")
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign:4.3.0")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.security:spring-security-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-integration")
    implementation("io.sentry:sentry-openfeign:7.22.5")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.8")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:2.8.8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.github.resilience4j:resilience4j-all:2.1.0")
    implementation("io.github.resilience4j:resilience4j-annotations:2.3.0")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("com.h2database:h2")
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("org.springframework.kafka:spring-kafka")
    
    compileOnly("org.springframework.boot:spring-boot-configuration-processor")
    
    testImplementation("com.h2database:h2")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    testImplementation("org.springframework.cloud:spring-cloud-contract-stub-runner")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.sentry:sentry-jdbc")
    testImplementation("org.springframework.cloud:spring-cloud-contract-wiremock")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("io.kotest:kotest-runner-junit5:5.4.0")
    testImplementation("io.kotest:kotest-assertions-core:5.4.0")
    testImplementation("io.kotest:kotest-framework-engine:5.4.0")
    testImplementation(testFixtures(project(":jar-auth")))
    
    testImplementation("org.beancounter:svc-position:0.1.1:stubs") {
        isTransitive = false
    }
}

springBoot {
    buildInfo()
}

gitProperties {
    failOnNoGitDirectory = false
}

tasks.named("bootBuildImage") {
    setProperty("imageName", "monowai/bc-event")
}

val isCI = System.getenv("CI")?.toBoolean() ?: false

sentry {
    if (isCI) {
        includeSourceContext = true
        org = "monowai-developments-ltd"
        projectName = "event"
        authToken = System.getenv("SENTRY_AUTH_TOKEN")
    }
}
