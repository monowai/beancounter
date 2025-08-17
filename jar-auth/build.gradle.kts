plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.java.test.fixtures)
}

version = "0.0.1"

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.4"))
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies:2025.0.0"))
    implementation(project(":jar-common"))
    implementation("jakarta.servlet:jakarta.servlet-api:6.1.0")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.springframework:spring-webmvc")
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign:4.3.0")
    implementation("org.springframework.security:spring-security-config")
    implementation("org.springframework.security:spring-security-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    
    compileOnly("com.fasterxml.jackson.module:jackson-module-kotlin")
    
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(module = "junit-vintage-engine")
    }
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.cloud:spring-cloud-starter-contract-stub-runner")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    testImplementation("org.springframework.boot:spring-boot-autoconfigure")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.security:spring-security-oauth2-client")
    
    testFixturesImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.4"))
    testFixturesImplementation(platform("org.springframework.cloud:spring-cloud-dependencies:2025.0.0"))
    testFixturesImplementation(project(":jar-common"))
    testFixturesImplementation("org.springframework.boot:spring-boot-starter-test")
    testFixturesImplementation("org.springframework.security:spring-security-config")
    testFixturesImplementation("org.springframework.security:spring-security-test")
    testFixturesImplementation("org.springframework.security:spring-security-oauth2-client")
    testFixturesImplementation("org.springframework.security:spring-security-oauth2-resource-server")
    testFixturesImplementation("org.springframework.security:spring-security-oauth2-jose")
}
