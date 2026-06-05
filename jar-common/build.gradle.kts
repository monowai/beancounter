plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.java.test.fixtures)
    alias(libs.plugins.sentry)
}

version = "0.1.3-SNAPSHOT"

extra["guavaVersion"] = "33.3.1-jre"

dependencies {
    api(libs.jackson.kotlin)
    api(libs.sentry.opentelemetry)
    api(libs.sentry.spring.boot)
    // Logback appender: ships ERROR+ events as Sentry Log records when
    // sentry.logs.enabled=true. Auto-registered by sentry-spring-boot
    // starter; no logback-spring.xml changes needed.
    api(libs.sentry.logback)
    // Bridges Micrometer Observations -> OTel spans so every Beancounter
    // service that pulls jar-common emits Spring AI / Spring MVC / Hikari
    // / Resilience4j / Spring Data observations as OTel spans, which the
    // sentry-opentelemetry-agent javaagent already exports to Sentry.
    // Pinned in the version catalog because some consumers (e.g. jar-shell)
    // don't import the spring-boot-dependencies platform.
    api(libs.micrometer.tracing.bridge.otel)

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // BOMs are `api` so downstream modules (jar-shell, svc-data, etc.) inherit
    // the version constraints for the `api(...)` artifacts below.
    api(platform(libs.spring.boot.dependencies))
    api(platform("io.opentelemetry:opentelemetry-bom:1.54.1"))
    implementation("io.opentelemetry:opentelemetry-sdk")
    // Bridges OTel `Context` to Kotlin coroutine `CoroutineContext` so the
    // current Span propagates across suspension and dispatcher switches
    // (e.g. `runBlocking(Dispatchers.IO)` inside a Spring MVC handler).
    // Exposed as `api` because consumers call `Context.current().asContextElement()`
    // when wrapping coroutine builders.
    api("io.opentelemetry:opentelemetry-extension-kotlin")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation(libs.servlet.api)
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation(libs.jackson.kotlin)
    implementation("org.springframework:spring-web")
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.commons.io)
    implementation(libs.guava)

    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.apache.commons", module = "commons-lang3")
        exclude(group = "org.apache.commons", module = "commons-text")
    }
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation(libs.servlet.api)
    testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    testImplementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    testImplementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation(libs.assertj)
    testImplementation(libs.guava)
    testImplementation("org.springframework:spring-web")
    testImplementation("org.junit.jupiter:junit-jupiter")

    testImplementation(libs.jackson.kotlin) {
        exclude(module = "kotlin-reflect")
    }

    // Test fixtures dependencies
    testFixturesImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.apache.commons", module = "commons-lang3")
        exclude(group = "org.apache.commons", module = "commons-text")
    }
}