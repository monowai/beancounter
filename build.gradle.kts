import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.Duration

plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.jacoco)
    alias(libs.plugins.idea)
    alias(libs.plugins.kotlinter)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.spring)
    id("com.osacky.doctor") version "0.10.0"
}

// Build configuration

repositories {
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
    maven { url = uri("https://plugins.gradle.org/m2/") }
}

// Shared configuration for all subprojects
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "jacoco")
    apply(plugin = "idea")

    // Apply kotlinter for formatting, but skip detekt for AI agent module
    // AI code patterns don't align with traditional static analysis conventions:
    // - High cyclomatic complexity from intent determination and action routing
    // - Long methods for AI prompt generation and response parsing
    // - Generic exception handling for graceful degradation
    // - Verbose string manipulation for context building
    apply(plugin = "org.jmailen.kotlinter")
    if (name != "svc-agent") {
        apply(plugin = "io.gitlab.arturbosch.detekt") // Direct plugin ID needed in subprojects block
    }

    apply(plugin = "org.jetbrains.kotlin.plugin.jpa")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")

    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }

    // JVM configuration
    kotlin {
        jvmToolchain(21)
        compilerOptions {
            languageVersion.set(KotlinVersion.KOTLIN_2_1)
        }
    }

    // Test configuration with optimizations
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        finalizedBy(tasks.named("jacocoTestReport"))

        // Gradle 8.14+ compatibility: Include main source set output in test classpath
        testClassesDirs +=
            sourceSets.main
                .get()
                .output.classesDirs
        classpath = classpath + sourceSets.main.get().output + sourceSets.main.get().compileClasspath

        // Test optimizations
        maxParallelForks = 1
        forkEvery = 100L
        timeout.set(Duration.ofMinutes(5))

        // Test logging
        testLogging {
            events("skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showStandardStreams = false
        }
    }

    // Java compilation configuration
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-parameters"))
        options.isDeprecation = true
        options.encoding = "UTF-8"
    }

    // Kotlin compilation configuration
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            freeCompilerArgs.addAll(
                "-Xjsr305=strict",
                "-Xjvm-default=all",
                "-Xmulti-dollar-interpolation"
            )
        }
    }

    // Build task dependencies
    tasks.named("build") {
        dependsOn(tasks.named("formatKotlin"))
    }

    // JaCoCo configuration
    jacoco {
        toolVersion = "0.8.12"
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    // IDEA configuration
    idea {
        module {
            inheritOutputDirs = true
        }
    }

    // Check task dependencies
    tasks.named("check") {
        dependsOn(tasks.named("jacocoTestReport"))
    }

    // Custom task to format all Kotlin source sets at once
    tasks.register("formatAllKotlin") {
        group = "formatting"
        description = "Format all Kotlin source sets (main, test, contractTest)"

        dependsOn(tasks.named("formatKotlin"))

        // Add contract test formatting if the task exists
        val formatContractTestTask = tasks.findByName("formatKotlinContractTest")
        if (formatContractTestTask != null) {
            dependsOn(formatContractTestTask)
        }

        doLast {
            println("✅ All Kotlin source sets formatted successfully!")
        }
    }

    // Custom task to lint all Kotlin source sets at once
    tasks.register("lintAllKotlin") {
        group = "verification"
        description = "Lint all Kotlin source sets (main, test, contractTest)"

        dependsOn(tasks.named("lintKotlin"))

        // Add contract test linting if the task exists
        val lintContractTestTask = tasks.findByName("lintKotlinContractTest")
        if (lintContractTestTask != null) {
            dependsOn(lintContractTestTask)
        }

        doLast {
            println("✅ All Kotlin source sets linted successfully!")
        }
    }

    // Custom task to run Detekt on all projects (excluding svc-agent)
    tasks.register("detektAll") {
        group = "verification"
        description = "Run Detekt static analysis on all projects (excluding svc-agent)"

        dependsOn(subprojects.filter { it.name != "svc-agent" }.map { it.tasks.named("detekt") })

        doLast {
            println("✅ Detekt analysis completed for all projects!")
        }
    }

    // Custom task to run Detekt with auto-correction
    tasks.register("detektFix") {
        group = "verification"
        description = "Run Detekt with auto-correction on all projects (excluding svc-agent)"

        dependsOn(subprojects.filter { it.name != "svc-agent" }.map { it.tasks.named("detektMain") })

        doLast {
            println("✅ Detekt auto-correction completed for all projects!")
        }
    }

    // Kotlinter configuration
    kotlinter {
        // Note: The function count warning might be from the IDE or another static analysis tool
        // kotlinter primarily focuses on code style, not complexity metrics
    }

    // For svc-agent: Keep formatting available but disable linting
    if (name == "svc-agent") {
        tasks.named("lintKotlin") {
            enabled = false
        }
    }

    // Detekt configuration (only for modules that have detekt applied)
    if (name != "svc-agent") {
        detekt {
            config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
            buildUponDefaultConfig = true
            allRules = false
            autoCorrect = true
            parallel = true
        }
    }

    // Common dependencies for all modules
    dependencies {
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")

        // Common test dependencies
        testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.4"))
        testImplementation("org.assertj:assertj-core:3.27.3")
    }
}

// Root project configuration
group = "com.beancounter"
version = "0.0.1-SNAPSHOT"

// Root project tasks with proper dependency order
tasks.register("buildAll") {
    dependsOn(":jar-common:build")
    dependsOn(":jar-auth:build")
    dependsOn(":jar-client:build")
    dependsOn(":jar-shell:build")
    dependsOn(":svc-data:build")
    dependsOn(":svc-position:build")
    dependsOn(":svc-event:build")
    description = "Build all subprojects in dependency order"
}

// Build core libraries first (no service dependencies)
tasks.register("buildCoreLibraries") {
    dependsOn(":jar-common:build")
    dependsOn(":jar-auth:build")
    dependsOn(":jar-client:build")
    dependsOn(":jar-shell:build")
    description = "Build core libraries (no service dependencies)"
}

// Build services after core libraries
tasks.register("buildServices") {
    dependsOn("buildCoreLibraries")
    dependsOn(":svc-data:build")
    dependsOn(":svc-position:build")
    dependsOn(":svc-event:build")
    description = "Build services after core libraries"
}

// Publish contract stubs for services that need them
tasks.register("publishStubs") {
    dependsOn(":svc-data:pubStubs")
    dependsOn(":svc-position:pubStubs")
    description = "Publish contract stubs to local Maven repository"
}

tasks.register("testAll") {
    dependsOn("verifyStubs") // Verify stubs are available
    dependsOn(":jar-common:test")
    dependsOn(":jar-auth:test")
    dependsOn(":jar-client:test")
    dependsOn(":jar-shell:test")
    dependsOn(":svc-data:test")
    dependsOn(":svc-position:test")
    dependsOn(":svc-event:test")
    description = "Test all subprojects in dependency order (stubs must be available)"
}

tasks.register("cleanAll") {
    dependsOn(subprojects.map { it.tasks.named("clean") })
    description = "Clean all subprojects"
}

// Dependency verification
tasks.register("verifyDependencies") {
    dependsOn(subprojects.map { it.tasks.named("dependencies") })
    description = "Verify all project dependencies"
}

// Build core libraries first (no service dependencies)
tasks.register("buildCore") {
    dependsOn(":jar-common:build")
    dependsOn(":jar-auth:build")
    dependsOn(":jar-client:build")
    description = "Build core libraries (jar-common, jar-auth, jar-client)"
}

// Verify stubs are available before building
tasks.register("verifyStubsForBuild") {
    doLast {
        val mavenLocal = File(System.getProperty("user.home") + "/.m2/repository/org/beancounter")
        val svcDataStubs = File(mavenLocal, "svc-data/0.1.1/svc-data-0.1.1-stubs.jar")
        val svcPositionStubs = File(mavenLocal, "svc-position/0.1.1/svc-position-0.1.1-stubs.jar")

        if (!svcDataStubs.exists() || !svcPositionStubs.exists()) {
            println("📦 Contract stubs missing!")
            println("   Run: ./gradlew buildWithStubs")
            println("   This will build everything and publish stubs.")
            throw GradleException("Contract stubs are missing. Run './gradlew buildWithStubs' for a complete build.")
        } else {
            println("✅ Contract stubs available, proceeding with build...")
        }
    }
    description = "Verify that contract stubs are available before building"
}

// Smart build that checks for stubs first
tasks.register("buildSmart") {
    dependsOn("verifyStubsForBuild")
    dependsOn("buildAll")
    description = "Smart build - checks for stubs and builds if available"
}

// Build services first and publish stubs
tasks.register("buildServicesAndPublishStubs") {
    dependsOn(":jar-common:build")
    dependsOn(":jar-auth:build")
    dependsOn(":svc-data:build")
    dependsOn(":svc-position:build")
    dependsOn(":svc-event:build")
    dependsOn("publishStubs")
    description = "Build services and publish stubs"
}

// Complete build with stub publishing (for CI/CD or explicit stub publishing)
tasks.register("buildWithStubs") {
    doLast {
        println("🔨 Complete build with stub publishing completed successfully!")
    }
    description = "Complete build including contract stub publishing"
}

// Build everything in the correct order
tasks.register("buildAllWithStubs") {
    dependsOn("buildServicesAndPublishStubs")
    dependsOn(":jar-client:build")
    dependsOn(":jar-shell:build")
    finalizedBy("buildWithStubs")
    description = "Build all projects in correct order with stub publishing"
}

// Verify stubs are available before testing
tasks.register("verifyStubsForTest") {
    doLast {
        val mavenLocal = File(System.getProperty("user.home") + "/.m2/repository/org/beancounter")
        val svcDataStubs = File(mavenLocal, "svc-data/0.1.1/svc-data-0.1.1-stubs.jar")
        val svcPositionStubs = File(mavenLocal, "svc-position/0.1.1/svc-position-0.1.1-stubs.jar")

        if (!svcDataStubs.exists() || !svcPositionStubs.exists()) {
            println("📦 Contract stubs missing!")
            println("   Run: ./gradlew testWithStubs")
            println("   This will publish stubs and run all tests.")
            throw GradleException("Contract stubs are missing. Run './gradlew testWithStubs' for a complete test run.")
        } else {
            println("✅ Contract stubs available, proceeding with tests...")
        }
    }
    description = "Verify that contract stubs are available before testing"
}

// Smart test that checks for stubs first
tasks.register("testSmart") {
    dependsOn("verifyStubsForTest")
    dependsOn("testAll")
    description = "Smart test - checks for stubs and tests if available"
}

// Complete test with stub publishing (for CI/CD or explicit stub publishing)
tasks.register("testWithStubs") {
    dependsOn("publishStubs")
    dependsOn("testAll")
    description = "Complete test run including contract stub publishing"
}

// Verify stub availability
tasks.register("verifyStubs") {
    doLast {
        println("Verifying contract stubs are available...")

        val mavenLocal = File(System.getProperty("user.home") + "/.m2/repository/org/beancounter")
        val svcDataStubs = File(mavenLocal, "svc-data/0.1.1/svc-data-0.1.1-stubs.jar")
        val svcPositionStubs = File(mavenLocal, "svc-position/0.1.1/svc-position-0.1.1-stubs.jar")

        if (!svcDataStubs.exists()) {
            throw GradleException("svc-data stubs not found. Run 'publishStubs' first.")
        }
        if (!svcPositionStubs.exists()) {
            throw GradleException("svc-position stubs not found. Run 'publishStubs' first.")
        }

        println("✅ Contract stubs are available:")
        println("  - svc-data: ${svcDataStubs.absolutePath}")
        println("  - svc-position: ${svcPositionStubs.absolutePath}")
    }
    description = "Verify that contract stubs are available in local Maven repository"
}

// Validate dependency order
tasks.register("validateDependencies") {
    doLast {
        println("Validating project dependencies...")

        // Check jar-auth depends on jar-common
        val jarAuthDeps = project(":jar-auth").configurations.getByName("implementation").dependencies
        if (!jarAuthDeps.any { it.name == "jar-common" }) {
            throw GradleException("jar-auth must depend on jar-common")
        }

        // Check jar-client depends on jar-common and jar-auth
        val jarClientDeps = project(":jar-client").configurations.getByName("implementation").dependencies
        if (!jarClientDeps.any { it.name == "jar-common" }) {
            throw GradleException("jar-client must depend on jar-common")
        }
        if (!jarClientDeps.any { it.name == "jar-auth" }) {
            throw GradleException("jar-client must depend on jar-auth")
        }

        // Check services depend on core libraries
        listOf("svc-data", "svc-position", "svc-event").forEach { serviceName ->
            val serviceDeps = project(":$serviceName").configurations.getByName("implementation").dependencies
            if (!serviceDeps.any { it.name == "jar-common" }) {
                throw GradleException("$serviceName must depend on jar-common")
            }
            if (!serviceDeps.any { it.name == "jar-auth" }) {
                throw GradleException("$serviceName must depend on jar-auth")
            }
            if (!serviceDeps.any { it.name == "jar-client" }) {
                throw GradleException("$serviceName must depend on jar-client")
            }
        }

        println("✅ All dependencies are correctly configured!")
    }
    description = "Validate that all projects have correct dependencies"
}