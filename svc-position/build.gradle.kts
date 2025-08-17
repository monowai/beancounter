plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.contract)
    alias(libs.plugins.java.test.fixtures)
    alias(libs.plugins.sentry)
    alias(libs.plugins.git.properties)
    alias(libs.plugins.download)
    `maven-publish`
}

version = "0.1.1"

publishing {
    publications {
        create<MavenPublication>("position-stubs") {
            artifactId = project.name
            groupId = "org.beancounter"
            version = project.version.toString()
            artifact(tasks.named("verifierStubsJar"))
        }
    }
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.4"))
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies:2025.0.0"))
    implementation(platform("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:2.12.0"))
    implementation(project(":jar-common"))
    implementation(project(":jar-client"))
    implementation(project(":jar-auth"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-logging")
    implementation("io.sentry:sentry-openfeign:7.22.5")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.8")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:2.8.8")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign:4.3.0")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.security:spring-security-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-integration")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.github.resilience4j:resilience4j-annotations:2.3.0")
    implementation("io.github.resilience4j:resilience4j-all:2.1.0")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.apache.commons:commons-math3:3.6.1")
    
    compileOnly("org.springframework.boot:spring-boot-configuration-processor")
    contractTestImplementation("org.springframework.cloud:spring-cloud-starter-contract-verifier")
    
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.cloud:spring-cloud-contract-wiremock")
    testImplementation("org.springframework.cloud:spring-cloud-contract-stub-runner")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation(testFixtures(project(":jar-auth")))
    
    testImplementation("org.beancounter:svc-data:0.1.1:stubs") {
        isTransitive = false
    }
}

contracts {
    testFramework.set(org.springframework.cloud.contract.verifier.config.TestFramework.JUNIT5)
    testMode.set(org.springframework.cloud.contract.verifier.config.TestMode.EXPLICIT)
    baseClassForTests.set("com.contracts.position.ContractVerifierBase")
}

tasks.register("pubStubs") {
    dependsOn("build")
    dependsOn("publishToMavenLocal")
}

springBoot {
    buildInfo()
}

gitProperties {
    failOnNoGitDirectory = false
}

// Contract test linting and formatting tasks - only configure if they exist
tasks.matching { it.name == "lintKotlinContractTest" }.configureEach {
    // Only add dependency if the contract test generation task exists
    val contractTask = tasks.findByName("generateContractTests") ?: tasks.findByName("generateContractTestSources")
    if (contractTask != null) {
        dependsOn(contractTask)
    }
}

tasks.matching { it.name == "formatKotlinContractTest" }.configureEach {
    // Only add dependency if the contract test generation task exists
    val contractTask = tasks.findByName("generateContractTests") ?: tasks.findByName("generateContractTestSources")
    if (contractTask != null) {
        dependsOn(contractTask)
    }
}

tasks.named("bootBuildImage") {
    setProperty("imageName", "monowai/bc-position")
}

tasks.named("sentryCollectSourcesJava") {
    mustRunAfter("formatKotlin")
}

val isCI = System.getenv("CI")?.toBoolean() ?: false

sentry {
    if (isCI) {
        includeSourceContext = true
        org = "monowai-developments-ltd"
        projectName = "position"
        authToken = System.getenv("SENTRY_AUTH_TOKEN")
    }
}
