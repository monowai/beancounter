version = "0.1.1"

extra["springShellVersion"] = "3.4.0"
extra["springBootVersion"] = libs.spring.boot.dependencies.get().version

dependencies {
    implementation(project(":jar-common"))
    implementation(project(":jar-auth"))
    implementation(project(":jar-client"))
    implementation(platform("org.springframework.shell:spring-shell-dependencies:3.4.0"))
    implementation("org.springframework.shell:spring-shell-starter:3.4.0")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-security:3.5.4")
    implementation("org.springframework.shell:spring-shell-starter-jansi")
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign:4.3.0")
    implementation("org.springframework.security:spring-security-oauth2-resource-server:6.4.4")
    implementation("org.springframework.security:spring-security-oauth2-jose:6.4.4")
    implementation("org.springframework.kafka:spring-kafka:3.3.8")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.0")
    implementation("com.opencsv:opencsv:5.11.1")
    
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation(testFixtures(project(":jar-auth")))
    testImplementation("org.springframework.security:spring-security-oauth2-resource-server:6.4.4")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.cloud:spring-cloud-contract-stub-runner:4.3.0")
    testImplementation("org.apache.groovy:groovy:4.0.26")
    testImplementation("org.springframework.kafka:spring-kafka-test:3.3.8")
    testImplementation("org.junit.jupiter:junit-jupiter")
    
    testImplementation("org.beancounter:svc-data:0.1.1:stubs") {
        isTransitive = false
    }
}
