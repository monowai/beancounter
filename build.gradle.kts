import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.Duration

plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.jacoco)
    alias(libs.plugins.idea)
    alias(libs.plugins.kotlinter)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.spring)
    `maven-publish`
}

// Build configuration

repositories {
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
    maven { url = uri("https://plugins.gradle.org/m2/") }
}

val agentModule = "svc-agent"
val githubRepoUrl = "https://github.com/monowai/beancounter"

// Shared configuration for all subprojects
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "jacoco")
    apply(plugin = "idea")

    apply(plugin = "org.jmailen.kotlinter")

    apply(plugin = "org.jetbrains.kotlin.plugin.jpa")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")

    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }

    // Exclude commons-logging to avoid conflicts with spring-jcl
    // Align all Sentry dependencies to avoid mixed versions warning
    configurations.all {
        exclude(group = "commons-logging", module = "commons-logging")
        resolutionStrategy.eachDependency {
            if (requested.group == "io.sentry") {
                useVersion("8.40.0")
                because("Align all Sentry dependencies to avoid mixed versions warning")
            }
        }
        // Force-pin transitive dependencies that have known CVEs but are
        // pulled in indirectly (so we can't bump them at the declaration site).
        // Review periodically — when the upstream BOM catches up, the force()
        // entry can be removed.
        resolutionStrategy {
            force(
                // CVE-2025-48924 — uncontrolled recursion / DoS
                "org.apache.commons:commons-lang3:3.20.0",
                // CVE-2025-48734 — unsafe reflection (test/contract tooling)
                "commons-beanutils:commons-beanutils:1.11.0"
            )
        }
    }

    // JVM configuration
    kotlin {
        jvmToolchain(25)
        compilerOptions {
            languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3)
        }
    }

    // Test configuration with optimizations
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // Gradle 9 flipped this default to true. Some modules (e.g. jar-contracts)
        // have no test sources of their own, so don't fail their :test task on
        // zero discovered tests.
        failOnNoDiscoveredTests = false
        finalizedBy(tasks.named("jacocoTestReport"))

        // Gradle 8.14+ compatibility: Include main source set output in test classpath
        // Skip this for contractTest task as it has its own source set
        if (name != "contractTest") {
            testClassesDirs +=
                sourceSets.main
                    .get()
                    .output.classesDirs
            classpath = classpath + sourceSets.main.get().output + sourceSets.main.get().compileClasspath
        }

        // Test optimizations
        maxParallelForks = 1
        forkEvery = 100L
        maxHeapSize = "1g"
        // svc-data tests have crept toward 8m on CircleCI's executor and tipped over on
        // main #32be9384 (build-and-test job 17895). Bump headroom; revisit if any
        // single test is genuinely runaway, but the existing suite is just slow.
        timeout.set(Duration.ofMinutes(15))

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
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
            freeCompilerArgs.addAll(
                "-Xjsr305=strict",
                "-jvm-default=enable",
                "-Xannotation-default-target=param-property"
            )
        }
    }

    // Build task dependencies
    tasks.named("build") {
        dependsOn(tasks.named("formatKotlin"))
    }

    // JaCoCo configuration
    jacoco {
        toolVersion = "0.8.15"
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

    // Kotlinter configuration
    kotlinter {
        // Note: The function count warning might be from the IDE or another static analysis tool
        // kotlinter primarily focuses on code style, not complexity metrics
    }

    // For svc-agent: Keep formatting available but disable linting
    if (name == agentModule) {
        tasks.findByName("lintKotlin")?.enabled = false
    }

    // Common dependencies for all modules
    dependencies {
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
        // Spring Framework 7 removed spring-jcl (the commons-logging provider),
        // and the configurations-wide exclude of commons-logging:commons-logging
        // (a Boot-3 workaround) leaves nothing supplying
        // org.apache.commons.logging.LogFactory. Without this, services fail at
        // STARTUP (NoClassDefFoundError), not just in tests. runtimeOnly covers
        // both main and test runtime; routed to the existing logback. BOM-managed.
        runtimeOnly("org.slf4j:jcl-over-slf4j")

        // Security patch overrides for BOM-managed transitives (Snyk). Spring
        // Boot 4.1.0 has no patch release yet, so pin fixed versions within the
        // same patch line to avoid minor-version skew. Remove each once the
        // managing BOM catches up.
        constraints {
            // jackson-databind 2.x/3.x: Incorrect Authorization (CVE-2026-59889)
            implementation("com.fasterxml.jackson.core:jackson-databind:2.21.5")
            implementation("tools.jackson.core:jackson-databind:3.1.5")
            // tomcat-embed-core: 6 CVEs incl. improper auth (CVE-2026-55955)
            implementation("org.apache.tomcat.embed:tomcat-embed-core:11.0.24")
            // logback-core: expression injection (CVE-2026-13006)
            implementation("ch.qos.logback:logback-core:1.5.38")
            implementation("ch.qos.logback:logback-classic:1.5.38")
            // httpcore5-h2: unbounded resource allocation (CVE-2026-54428)
            implementation("org.apache.httpcomponents.core5:httpcore5-h2:5.4.3")
            // handlebars: directory traversal (CVE-2026-55760); via cloud-contract
            implementation("com.github.jknack:handlebars:4.5.2")
            // guava: 2018 deserialization (CVE-2018-10237); via stub-runner (test)
            implementation("com.google.guava:guava:33.6.0-jre")
        }

        // Common test dependencies
        testImplementation(
            platform("org.springframework.boot:spring-boot-dependencies:${rootProject.libs.versions.spring.boot.get()}")
        )
        testImplementation(rootProject.libs.assertj)
    }

    // Publishing configuration for library modules (jar-common, jar-auth, jar-client)
    if (name.startsWith("jar-")) {
        apply(plugin = "maven-publish")
        apply(plugin = "java-library")

        // Ensure group is set for publishing
        group = "com.beancounter"

        configure<PublishingExtension> {
            publications {
                create<MavenPublication>("mavenJava") {
                    groupId = "com.beancounter"
                    from(components["java"])

                    pom {
                        name.set(project.name)
                        description.set("Beancounter ${project.name} library")
                        url.set(githubRepoUrl)

                        licenses {
                            license {
                                name.set("MIT License")
                                url.set("https://opensource.org/licenses/MIT")
                            }
                        }

                        developers {
                            developer {
                                id.set("monowai")
                                name.set("Mike Holdsworth")
                            }
                        }

                        scm {
                            connection.set("scm:git:git://github.com/monowai/beancounter.git")
                            developerConnection.set("scm:git:ssh://github.com/monowai/beancounter.git")
                            url.set(githubRepoUrl)
                        }
                    }
                }
            }

            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/monowai/beancounter")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") as String? ?: ""
                        password = System.getenv("GH_GCR") ?: project.findProperty("gpr.key") as String? ?: ""
                    }
                }
                // Local Maven repository for testing
                mavenLocal()
            }
        }
    }
}

// Root project configuration
group = "com.beancounter"
version = "0.0.1-SNAPSHOT"

// Root project tasks. Contract stubs flow between modules as regular Gradle
// artifacts (svc-data/svc-position expose a `stubs` configuration consumed by
// jar-client, jar-shell, svc-position and svc-event), so no manual build
// ordering or ~/.m2 stub publishing is required — plain `./gradlew build` works
// from a clean checkout.
tasks.register("buildAll") {
    dependsOn(subprojects.map { "${it.path}:build" })
    description = "Build all subprojects"
}

// Publish contract stubs for services that need them
tasks.register("publishStubs") {
    dependsOn(":svc-data:pubStubs")
    dependsOn(":svc-position:pubStubs")
    description = "Publish contract stubs to local Maven repository"
}

// Publish jar libraries to Maven repository.
// jar-contracts MUST be published: jar-common exposes it as an `api`
// dependency, so jar-common's POM references it — external consumers
// (svc-retire, svc-rebalance) fail to resolve jar-common without it.
tasks.register("publishJars") {
    dependsOn(":jar-contracts:publish")
    dependsOn(":jar-common:publish")
    dependsOn(":jar-auth:publish")
    dependsOn(":jar-client:publish")
    description = "Publish jar-contracts, jar-common, jar-auth, and jar-client to Maven repository"
}

// Publish jar libraries to local Maven repository
tasks.register("publishJarsLocal") {
    dependsOn(":jar-contracts:publishToMavenLocal")
    dependsOn(":jar-common:publishToMavenLocal")
    dependsOn(":jar-auth:publishToMavenLocal")
    dependsOn(":jar-client:publishToMavenLocal")
    description = "Publish jar-contracts, jar-common, jar-auth, and jar-client to local Maven repository"
}

tasks.register("testAll") {
    dependsOn(subprojects.map { "${it.path}:test" })
    // contractTest is a separate source set/task on the stub producers and is
    // not wired into `test` — include it so testAll covers contract checks.
    dependsOn(":svc-data:contractTest")
    dependsOn(":svc-position:contractTest")
    description = "Test all subprojects incl. contract tests (stub ordering handled by Gradle)"
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

// Legacy aliases: stubs are now wired as Gradle project artifacts, so the
// old "verify stubs exist in ~/.m2 first" dance is gone. Kept so existing
// muscle memory / scripts / docs keep working.
tasks.register("buildSmart") {
    dependsOn("buildAll")
    description = "Alias for buildAll (stub ordering handled by Gradle)"
}

tasks.register("testSmart") {
    dependsOn("testAll")
    description = "Alias for testAll (stub ordering handled by Gradle)"
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

        // Check services depend on core libraries. svc-data must NOT depend on
        // jar-client — that edge re-creates the circular build dependency
        // (jar-client tests consume svc-data's contract stubs).
        listOf("svc-data", "svc-position", "svc-event").forEach { serviceName ->
            val serviceDeps = project(":$serviceName").configurations.getByName("implementation").dependencies
            if (!serviceDeps.any { it.name == "jar-common" }) {
                throw GradleException("$serviceName must depend on jar-common")
            }
            if (!serviceDeps.any { it.name == "jar-auth" }) {
                throw GradleException("$serviceName must depend on jar-auth")
            }
        }
        listOf("svc-position", "svc-event").forEach { serviceName ->
            val serviceDeps = project(":$serviceName").configurations.getByName("implementation").dependencies
            if (!serviceDeps.any { it.name == "jar-client" }) {
                throw GradleException("$serviceName must depend on jar-client")
            }
        }
        val svcDataDeps = project(":svc-data").configurations.getByName("implementation").dependencies
        if (svcDataDeps.any { it.name == "jar-client" }) {
            throw GradleException("svc-data must NOT depend on jar-client (circular stub dependency)")
        }

        println("✅ All dependencies are correctly configured!")
    }
    description = "Validate that all projects have correct dependencies"
}