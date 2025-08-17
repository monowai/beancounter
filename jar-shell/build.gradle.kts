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
    implementation(libs.spring.cloud.feign) {
        exclude(group = "org.apache.commons", module = "commons-lang3")
    }
    implementation(libs.spring.security.oauth2)
    implementation(libs.spring.security.jose)
    implementation(libs.spring.kafka)
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.0")
    implementation("com.opencsv:opencsv:5.11.1")
    
    testImplementation(libs.assertj)
    testImplementation(testFixtures(project(":jar-auth")))
    testImplementation(libs.spring.security.oauth2)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.spring.stub.runner)
    testImplementation(libs.apache.groovy)
    testImplementation(libs.spring.kafka.test)
    testImplementation("org.junit.jupiter:junit-jupiter")
    
    testImplementation("org.beancounter:svc-data:0.1.1:stubs") {
        isTransitive = false
    }
}
