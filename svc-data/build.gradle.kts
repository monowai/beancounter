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
        create<MavenPublication>("data-stubs") {
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
    implementation(project(":jar-auth"))
    implementation(project(":jar-client"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-logging")
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign:4.3.0")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.8")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:2.8.8")
    implementation("io.sentry:sentry-openfeign:7.22.5")
    implementation("io.sentry:sentry-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.security:spring-security-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-integration")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("io.github.resilience4j:resilience4j-all:2.1.0")
    implementation("io.github.resilience4j:resilience4j-annotations:2.3.0")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("com.h2database:h2") // Dev
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("com.opencsv:opencsv:5.11.1")
    
    compileOnly("org.springframework.boot:spring-boot-configuration-processor")
    contractTestImplementation("org.springframework.cloud:spring-cloud-starter-contract-verifier")
    
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.cloud:spring-cloud-contract-wiremock")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.junit.platform:junit-platform-suite-api")
    testImplementation("org.junit.platform:junit-platform-suite-engine")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation(testFixtures(project(":jar-auth")))
}

contracts {
    testMode.set(org.springframework.cloud.contract.verifier.config.TestMode.EXPLICIT)
    failOnInProgress.set(false)
    testFramework.set(org.springframework.cloud.contract.verifier.config.TestFramework.JUNIT5)
    failOnNoContracts.set(false)
    packageWithBaseClasses.set("com.contracts.data")
    baseClassForTests.set("com.contracts.data.ContractVerifierBase")
}

tasks.named<Test>("contractTest") {
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.register<Test>("testSuites") {
    include("**/suites/**")
    useJUnitPlatform()
}

tasks.named<Test>("test") {
    exclude("**/suites/**")
}

tasks.register("pubStubs") {
    dependsOn("build")
    dependsOn("publishToMavenLocal")
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

tasks.withType<JavaCompile>().configureEach {
    options.isFork = false
}

springBoot {
    buildInfo()
}

gitProperties {
    failOnNoGitDirectory = false
}

tasks.named("bootBuildImage") {
    setProperty("imageName", "monowai/bc-data")
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

tasks.matching { it.name == "sentryCollectSourcesJava" }.configureEach {
    // Only add dependency if the contract test generation task exists
    val contractTask = tasks.findByName("generateContractTests") ?: tasks.findByName("generateContractTestSources")
    if (contractTask != null) {
        dependsOn(contractTask)
        mustRunAfter(contractTask)
    }
}
