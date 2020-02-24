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

Useful references:
https://blog.jdriven.com/2019/10/spring-security-5-2-oauth-2-exploration-part1/
https://docs.spring.io/spring-security/site/docs/current/reference/html5/
https://www.keycloak.org/docs/latest/authorization_services/
https://stackoverflow.com/questions/19525380/difference-between-role-and-grantedauthority-in-spring-security
https://stackoverflow.com/questions/58205510/spring-security-mapping-oauth2-claims-with-roles-to-secure-resource-server-endp

Analyze your enciprted token
https://jwt.io/

Cautionary tales...
https://paragonie.com/blog/2017/03/jwt-json-web-tokens-is-bad-standard-that-everyone-should-avoid