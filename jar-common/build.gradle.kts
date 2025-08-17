plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.java.test.fixtures)
    alias(libs.plugins.sentry)
}

extra["guavaVersion"] = "33.3.1-jre"

dependencies {
    api("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    api("io.sentry:sentry-opentelemetry-core:7.22.5")
    
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.4"))
    implementation(platform("io.opentelemetry:opentelemetry-bom:1.46.0"))
    implementation("io.opentelemetry:opentelemetry-sdk")
    implementation("jakarta.servlet:jakarta.servlet-api:6.1.0")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.springframework:spring-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign:4.3.0")
    implementation("commons-io:commons-io:2.18.0")
    implementation("com.google.guava:guava:33.4.0-jre")
    
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("jakarta.servlet:jakarta.servlet-api:6.1.0")
    testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    testImplementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    testImplementation("org.springframework.kafka:spring-kafka")
    testImplementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.assertj:assertj-core")
    testImplementation("com.google.guava:guava:33.4.0-jre")
    testImplementation("org.springframework:spring-web")
    testImplementation("org.junit.jupiter:junit-jupiter")
    
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin") {
        exclude(module = "kotlin-reflect")
    }
    
    // Test fixtures dependencies
    testFixturesImplementation("org.springframework.boot:spring-boot-starter-test")
}
