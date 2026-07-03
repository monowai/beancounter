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
    implementation(platform(libs.spring.boot.dependencies))
    implementation(platform(libs.spring.cloud.dependencies))
    implementation(platform(libs.spring.ai.bom))
    implementation(platform(libs.otel.bom))
    implementation(project(":jar-common"))
    implementation(project(":jar-auth"))
    implementation(project(":jar-client"))
    implementation(libs.spring.boot.starter.web)
    // Pooled HTTP client for external RestClients (ExternalApiRestClientConfig): keep-alive
    // connection reuse avoids a fresh DNS lookup per request during the price-refresh burst.
    // Version managed by the Spring Boot BOM.
    implementation("org.apache.httpcomponents.client5:httpclient5")
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.logging)
    implementation(libs.spring.doc)
    implementation(libs.spring.doc.mvc)
    implementation(libs.sentry.jdbc)
    // Boot 4.1.0 no longer publishes spring-boot-starter-aop; AopAutoConfiguration
    // ships in spring-boot-autoconfigure and only aspectjweaver is needed for the
    // resilience4j @CircuitBreaker/@Retry aspects. Version managed by the Boot BOM.
    implementation("org.aspectj:aspectjweaver")
    // Boot 4 no longer manages/transitively supplies spring-retry (was pulled via
    // the removed spring-boot-starter-aop in Boot 3). CloudConfig uses @EnableRetry.
    implementation("org.springframework.retry:spring-retry:2.0.13")
    implementation(libs.spring.boot.starter.security)
    implementation("org.springframework.security:spring-security-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation(libs.spring.boot.starter.actuator)
    // Spring Boot Admin client. Inactive unless `spring.boot.admin.client.enabled=true`
    // (default false in application.yml). Enabled in kauri via env var; see bc-deploy.
    implementation("de.codecentric:spring-boot-admin-starter-client:4.1.1")
    implementation(libs.spring.boot.starter.integration)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation(libs.resilience4j.spring.boot3)
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("com.h2database:h2") // Dev
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)
    implementation(libs.spring.cloud.stream)
    implementation(libs.spring.cloud.stream.binder.rabbit)
    implementation("com.opencsv:opencsv:5.11.1")

    compileOnly(libs.spring.boot.configuration.processor)
    contractTestImplementation("org.springframework.cloud:spring-cloud-starter-contract-verifier")
    contractTestRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.apache.commons", module = "commons-lang3")
        exclude(group = "org.apache.commons", module = "commons-text")
    }
    testImplementation("tools.jackson.core:jackson-databind")
    // Boot 4 split @AutoConfigureMockMvc into the spring-boot-webmvc-test module.
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.cloud:spring-cloud-contract-wiremock")
    testImplementation(libs.spring.cloud.stream.test.binder)
    testImplementation("org.junit.platform:junit-platform-suite-api")
    testImplementation("org.junit.platform:junit-platform-suite-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.mockito.kotlin)
    testImplementation(testFixtures(project(":jar-auth")))
}

// RestAssured 5.5.7's legacy Groovy HTTPBuilder (used by EXPLICIT-mode contract
// tests for the real HTTP send) is incompatible with Groovy 5, which the Boot 4 /
// spring-cloud Oakwood BOM pulls in -> NPE in ClosureMetaClass.invokeOnDelegationObject.
// Pin Groovy 4 on the contract-test classpath only; contract *generation* runs on
// the plugin classpath and is unaffected.
configurations.matching { it.name.startsWith("contractTest") }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.apache.groovy") {
            useVersion("4.0.28")
        }
    }
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
    buildInfo {
        properties {
            additional.set(mapOf(
                "ci.buildNumber" to (System.getenv("CIRCLE_BUILD_NUM") ?: "local")
            ))
        }
    }
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
        projectName = "data"
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
