plugins {
    alias(libs.plugins.jvm)
    // all-open so the @Service / @Configuration util beans can be proxied.
    alias(libs.plugins.kotlin.spring)
}

version = "0.1.3-SNAPSHOT"

// Boot-agnostic tier: contracts, model, input, exception types, utils,
// accrual, composite, event. Depends only on Jackson, jakarta annotation
// APIs and plain spring-context/-beans (ABI-stable across Spring 6 -> 7),
// so the same artifact is consumable by both Spring Boot 3 and Boot 4
// services. No spring-web, spring-data-jpa runtime, servlet or security here.
dependencies {
    // Platform pins versions only; it does not force a Boot runtime on consumers.
    api(platform(libs.spring.boot.dependencies))

    api(libs.jackson.kotlin)
    // Jackson 3: JSR-310 (java.time) support is built into jackson-databind;
    // the standalone jackson-datatype-jsr310 module no longer exists.
    implementation("tools.jackson.core:jackson-databind")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Models carry JPA annotations (entity/DTO split deferred to Phase 2).
    // Expose the annotation API as `api` so persisting consumers (svc-data)
    // see them.
    api("jakarta.persistence:jakarta.persistence-api")
    implementation("jakarta.annotation:jakarta.annotation-api")
    // Models also use Hibernate-specific annotations (@JdbcTypeCode,
    // @CreationTimestamp). compileOnly: the annotations have no runtime
    // readers outside svc-data (which brings Hibernate via data-jpa), so
    // they stay off the runtime classpath of Boot-agnostic consumers.
    // Phase 2 (entity/DTO split) removes this coupling entirely.
    compileOnly("org.hibernate.orm:hibernate-core")

    // Util beans use @Service/@Configuration/@Value — spring-context only.
    api("org.springframework:spring-context")
    // slf4j facade for util loggers (ABI-stable across versions).
    api("org.slf4j:slf4j-api")

    implementation(libs.guava)
    implementation(libs.commons.io)

    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.apache.commons", module = "commons-lang3")
        exclude(group = "org.apache.commons", module = "commons-text")
    }
    testImplementation(libs.assertj)
    testImplementation("org.junit.jupiter:junit-jupiter")
}
