plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.jacoco)
    alias(libs.plugins.kotlinter)
}

version = "0.1.1-SNAPSHOT"

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(platform(libs.spring.cloud.dependencies))
    implementation(project(":jar-common"))
    implementation(project(":jar-auth"))
    implementation(libs.guava)
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.springframework.security:spring-security-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation(libs.jackson.kotlin)
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(libs.resilience4j)
    implementation(libs.resilience4j.annotations)
    implementation("org.springframework:spring-web")
    implementation(libs.spring.boot.starter.logging)
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.servlet.api)
    implementation("jakarta.annotation:jakarta.annotation-api")
    
    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.apache.commons", module = "commons-lang3")
        exclude(group = "org.apache.commons", module = "commons-text")
    }
    testImplementation(libs.assertj)
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.cloud:spring-cloud-contract-wiremock")
    testImplementation("org.springframework.cloud:spring-cloud-contract-stub-runner")
    testImplementation(libs.spring.boot.starter.webflux)
    testImplementation(testFixtures(project(":jar-auth")))
    
    testImplementation("org.beancounter:svc-data:0.1.1:stubs") {
        isTransitive = false
    }
}
