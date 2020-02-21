## Keycloak/OAuth Support for Services

To keep DNS simple, add a /etc/hosts entry for keycloak

Add the following config to your file, changing URI and realm as appropriate
 
```$yaml

keycloak:
  uri: "http://keycloak:9620"
  realm: "bc-dev"

spring:
  application:
    name: bc-positionKeeper
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: "${auth.uri}/auth/realms/${auth.realm}/protocol/openid-connect/certs"

```

SpringBoot app will want to include some dependencies

```$groovy

    implementation(
            project(":jar-auth"),
            "org.springframework.boot:spring-boot-starter-security",
            "org.springframework.security:spring-security-oauth2-resource-server",
            "org.springframework.security:spring-security-oauth2-jose",

```