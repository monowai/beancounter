plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.java.test.fixtures)
}

version = "0.1.2-SNAPSHOT"

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(platform(libs.spring.cloud.dependencies))
    implementation(project(":jar-common"))
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.spring.boot.starter.logging)
    implementation(libs.servlet.api)
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.springframework:spring-webmvc")
    implementation("org.springframework.security:spring-security-config")
    implementation("org.springframework.security:spring-security-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    
    compileOnly(libs.jackson.kotlin)
    
    testImplementation(libs.spring.boot.starter.test) {
        exclude(module = "junit-vintage-engine")
        exclude(group = "org.apache.commons", module = "commons-lang3")
        exclude(group = "org.apache.commons", module = "commons-text")
    }
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(libs.assertj)
    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.apache.commons", module = "commons-lang3")
        exclude(group = "org.apache.commons", module = "commons-text")
    }
    testImplementation("org.springframework.cloud:spring-cloud-starter-contract-stub-runner")
    testImplementation(libs.jackson.kotlin)
    testImplementation(libs.spring.boot.autoconfigure)
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.security:spring-security-oauth2-client")
    
    testFixturesImplementation(platform(libs.spring.boot.dependencies))
    testFixturesImplementation(platform(libs.spring.cloud.dependencies))
    testFixturesImplementation(project(":jar-common"))
    testFixturesImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.apache.commons", module = "commons-lang3")
        exclude(group = "org.apache.commons", module = "commons-text")
    }
    testFixturesImplementation("org.springframework.security:spring-security-config")
    testFixturesImplementation("org.springframework.security:spring-security-test")
    testFixturesImplementation("org.springframework.security:spring-security-oauth2-client")
    testFixturesImplementation("org.springframework.security:spring-security-oauth2-resource-server")
    testFixturesImplementation("org.springframework.security:spring-security-oauth2-jose")
}
