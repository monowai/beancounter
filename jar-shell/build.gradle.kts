version = "0.1.1"

extra["springShellVersion"] = "3.4.0"
extra["springBootVersion"] = libs.spring.boot.dependencies.get().version

dependencies {
    implementation(project(":jar-common"))
    implementation(project(":jar-auth"))
    implementation(project(":jar-client"))
    implementation(platform("org.springframework.shell:spring-shell-dependencies:3.4.0"))
    implementation("org.springframework.shell:spring-shell-starter:3.4.0")
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.security)
    implementation("org.springframework.shell:spring-shell-starter-jansi")
    implementation(libs.spring.cloud.feign) {
        exclude(group = "org.apache.commons", module = "commons-lang3")
        exclude(group = "org.apache.commons", module = "commons-text")
    }
    implementation(libs.spring.security.oauth2)
    implementation(libs.spring.security.jose)
    implementation(libs.spring.kafka)
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.0")
    implementation("com.opencsv:opencsv:5.11.1")
    
    testImplementation(libs.assertj)
    testImplementation(testFixtures(project(":jar-auth")))
    testImplementation(libs.spring.security.oauth2)
    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.apache.commons", module = "commons-lang3")
        exclude(group = "org.apache.commons", module = "commons-text")
    }
    testImplementation(libs.spring.stub.runner)
    testImplementation(libs.apache.groovy)
    testImplementation(libs.spring.kafka.test)
    testImplementation("org.junit.jupiter:junit-jupiter")
    
    testImplementation("org.beancounter:svc-data:0.1.1:stubs") {
        isTransitive = false
    }
}
