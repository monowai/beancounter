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
    implementation(platform(libs.spring.boot.dependencies))
    implementation(platform(libs.spring.cloud.dependencies))
    implementation(project(":jar-common"))
    implementation(project(":jar-auth"))
    implementation(project(":jar-client"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.logging)
    implementation(libs.spring.cloud.feign) {
        exclude(group = "org.apache.commons", module = "commons-lang3")
        exclude(group = "org.apache.commons", module = "commons-text")
    }
    implementation(libs.spring.boot.starter.security)
    implementation("org.springframework.security:spring-security-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.integration)
    implementation(libs.sentry.openfeign)
    implementation(libs.spring.doc)
    implementation(libs.spring.doc.mvc)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation(libs.jackson.kotlin)
    implementation(libs.resilience4j)
    implementation(libs.resilience4j.annotations)
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("com.h2database:h2")
    implementation(libs.postgresql)
    implementation(libs.spring.kafka)
    
    compileOnly(libs.spring.boot.configuration.processor)
    
    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.apache.commons", module = "commons-lang3")
        exclude(group = "org.apache.commons", module = "commons-text")
    }
    testImplementation("com.h2database:h2")
    testImplementation(libs.jackson.kotlin)
    testImplementation("org.springframework.cloud:spring-cloud-contract-stub-runner")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.sentry:sentry-jdbc")
    testImplementation("org.springframework.cloud:spring-cloud-contract-wiremock")
    testImplementation(libs.spring.kafka.test)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.framework.engine)
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
