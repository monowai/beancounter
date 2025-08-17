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
    implementation(platform(libs.spring.boot.dependencies))
    implementation(platform(libs.spring.cloud.dependencies))
    implementation(platform(libs.otel.bom))
    implementation(project(":jar-common"))
    implementation(project(":jar-client"))
    implementation(project(":jar-auth"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.logging)
    implementation(libs.sentry.openfeign)
    implementation(libs.spring.doc)
    implementation(libs.spring.doc.mvc)
    implementation(libs.spring.boot.starter.aop)
    implementation(libs.spring.cloud.feign) {
        exclude(group = "org.apache.commons", module = "commons-lang3")
    }
    implementation(libs.spring.boot.starter.security)
    implementation("org.springframework.security:spring-security-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.integration)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation(libs.jackson.kotlin)
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(libs.resilience4j.annotations)
    implementation(libs.resilience4j)
    implementation(libs.spring.kafka)
    implementation("org.apache.commons:commons-math3:3.6.1")
    
    compileOnly(libs.spring.boot.configuration.processor)
    contractTestImplementation("org.springframework.cloud:spring-cloud-starter-contract-verifier")
    
    testImplementation(libs.spring.boot.starter.test)
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.cloud:spring-cloud-contract-wiremock")
    testImplementation("org.springframework.cloud:spring-cloud-contract-stub-runner")
    testImplementation(libs.spring.kafka.test)
    testImplementation(libs.mockito.kotlin)
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
