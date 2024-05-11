# Beancounter Auth Module

This module secures services using OAuth2 and JWT tokens.
It provides a simple configuration mechanism and can be tested using `MockMvc`.

## Key Classes

- `AuthTest`: Demonstrates how to test the Auth mechanism.
- `SecurityConfig`: Enables or disables security in your service.
- `ResourceServerConfig`: Negotiates and converts tokens with the JWT endpoint.
- `JwtRoleConverter`: Converts KeyCloak realm claims to Spring roles.
- `TokenService`: Extracts the `AuthenticationToken` from the `SecurityContext`.
- `TokenUtils`: Returns JWT tokens for testing.
- `AuthorityRoleConverter`: Used by `MockMvc` to extract realm claims from the JWT.

## Configuration

This module exports test fixtures. Add the following to your `build.gradle`:

```groovy
implementation(
  project(":jar-auth"),
  "org.springframework.boot:spring-boot-starter-security",
  "org.springframework.security:spring-security-oauth2-resource-server",
  "org.springframework.security:spring-security-oauth2-jose",
)
testImplementation(
  testFixtures(project(":jar-auth")),
)
