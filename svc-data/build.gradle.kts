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

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.springframework.data" && requested.name != "spring-data-bom") {
            useVersion("3.5.3")
            because("Force Spring Data 2025.0.3 to fix deleteBy regression in 3.5.4")
        }
    }
}

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
    implementation(platform(libs.spring.data.bom))
    implementation(platform(libs.spring.boot.dependencies))
    implementation(platform(libs.spring.cloud.dependencies))
    implementation(platform(libs.spring.ai.bom))
    implementation(platform(libs.otel.bom))
    implementation(project(":jar-common"))
    implementation(project(":jar-auth"))
    implementation(project(":jar-client"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.logging)
    implementation(libs.spring.doc)
    implementation(libs.spring.doc.mvc)
    implementation(libs.sentry.jdbc)
    implementation(libs.spring.boot.starter.aop)
    implementation(libs.spring.boot.starter.security)
    implementation("org.springframework.security:spring-security-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.integration)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation(libs.resilience4j)
    implementation(libs.resilience4j.annotations)
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("com.h2database:h2") // Dev
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)
    implementation(libs.spring.cloud.stream)
    implementation(libs.spring.cloud.stream.binder.kafka)
    implementation(libs.spring.cloud.stream.binder.rabbit)
    implementation("com.opencsv:opencsv:5.11.1")
    implementation(libs.spring.ai.starter.mcp.server.webmvc)
    
    compileOnly(libs.spring.boot.configuration.processor)
    contractTestImplementation("org.springframework.cloud:spring-cloud-starter-contract-verifier")
    contractTestRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.apache.commons", module = "commons-lang3")
        exclude(group = "org.apache.commons", module = "commons-text")
    }
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.cloud:spring-cloud-contract-wiremock")
    testImplementation(libs.spring.cloud.stream.test.binder)
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.junit.platform:junit-platform-suite-api")
    testImplementation("org.junit.platform:junit-platform-suite-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.mockito.kotlin)
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
    useJUnitPlatform()
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
