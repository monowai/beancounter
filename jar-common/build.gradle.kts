plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.java.test.fixtures)
    alias(libs.plugins.sentry)
}

version = "0.1.0"

extra["guavaVersion"] = "33.3.1-jre"

dependencies {
    api(libs.jackson.kotlin)
    api(libs.sentry.opentelemetry)

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(platform(libs.spring.boot.dependencies))
    implementation(platform("io.opentelemetry:opentelemetry-bom:1.54.1"))
    implementation("io.opentelemetry:opentelemetry-sdk")
    implementation(libs.servlet.api)
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation(libs.jackson.kotlin)
    implementation("org.springframework:spring-web")
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.kafka)
    implementation(libs.spring.cloud.feign) {
        exclude(group = "org.apache.commons", module = "commons-lang3")
        exclude(group = "org.apache.commons", module = "commons-text")
    }
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
    testImplementation(libs.spring.kafka)
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