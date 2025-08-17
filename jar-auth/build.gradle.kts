plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.java.test.fixtures)
}

version = "0.0.1"

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(platform(libs.spring.cloud.dependencies))
    implementation(project(":jar-common"))
    implementation(libs.servlet.api)
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.springframework:spring-webmvc")
    implementation(libs.spring.cloud.feign) {
        exclude(group = "org.apache.commons", module = "commons-lang3")
    }
    implementation("org.springframework.security:spring-security-config")
    implementation("org.springframework.security:spring-security-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    
    compileOnly(libs.jackson.kotlin)
    
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(module = "junit-vintage-engine")
    }
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(libs.assertj)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.cloud:spring-cloud-starter-contract-stub-runner")
    testImplementation(libs.jackson.kotlin)
    testImplementation("org.springframework.boot:spring-boot-autoconfigure")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.security:spring-security-oauth2-client")
    
    testFixturesImplementation(platform(libs.spring.boot.dependencies))
    testFixturesImplementation(platform(libs.spring.cloud.dependencies))
    testFixturesImplementation(project(":jar-common"))
    testFixturesImplementation("org.springframework.boot:spring-boot-starter-test")
    testFixturesImplementation("org.springframework.security:spring-security-config")
    testFixturesImplementation("org.springframework.security:spring-security-test")
    testFixturesImplementation("org.springframework.security:spring-security-oauth2-client")
    testFixturesImplementation("org.springframework.security:spring-security-oauth2-resource-server")
    testFixturesImplementation("org.springframework.security:spring-security-oauth2-jose")
}
