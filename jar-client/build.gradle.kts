plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.jacoco)
    alias(libs.plugins.kotlinter)
}

group = "com.beancounter"
version = "0.0.1-SNAPSHOT"

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.4"))
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies:2025.0.0"))
    implementation(project(":jar-common"))
    implementation(project(":jar-auth"))
    implementation("com.google.guava:guava:33.4.0-jre")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.springframework.security:spring-security-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.github.resilience4j:resilience4j-all:2.1.0")
    implementation("io.github.resilience4j:resilience4j-annotations:2.3.0")
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign:4.3.0")
    
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.cloud:spring-cloud-contract-wiremock")
    testImplementation("org.springframework.cloud:spring-cloud-contract-stub-runner")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation(testFixtures(project(":jar-auth")))
    
    testImplementation("org.beancounter:svc-data:0.1.1:stubs") {
        isTransitive = false
    }
}
