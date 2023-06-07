# BeanCounter Auth Module

Services are secured using OAuth2 and the exchange of JWT tokens.

This guide is focused on how to make the security mechanism work and how to test it.

I wanted to be able to:

*   Use `MockMvc` to test my Auth mechanism
*   Have a simple configuration mechanism that enabled security to "just work"

## Key Classes

The easiest way to see how things work is to check out the `AuthTest` class which injects a simple secured controller
and then uses the various classes, via MockMvc, to verify security

`SecurityConfig` Enable/Disable security _in your service_ - run with `nosecurity` to disable AUTH mechanisms

`ResourceServerConfig`  the ResourceServer that negotiates and converts tokens with the JWT endpoint (KeyCloak)

`JwtRoleConverter` Used by `ResourceServerConfig` to convert KC realm claims to Spring roles

`TokenService` Handy dandy means of extracting the `AuthenticationToken` from the `SecurityContext`

`TokenUtils` Utility class that returns JWT tokens that can be used for testing purposes

`AuthorityRoleConverter` Used by `MockMvc` to extract realm claims from the JWT

### Configuring your service

```$groovy
implementation(
        project(":jar-auth"),
        "org.springframework.boot:spring-boot-starter-security",
        "org.springframework.security:spring-security-oauth2-resource-server",
        "org.springframework.security:spring-security-oauth2-jose",
```

Add the following config to your file, changing URI and realm as appropriate

```$yaml

keycloak:
  uri: "http://keycloak:9620"
  realm: "bc-dev"

spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: "${auth.uri}/auth/realms/${auth.realm}/protocol/openid-connect/certs"

```

## Scope and Roles

This is an example of how roles and scopes are returned in a JWT token

```json
{
  "realm_access": {
    "roles": [
      "offline_access",
      "uma_authorization",
      "user"
    ]
  },
  "scope": "beancounter profile email roles"
}
```

In BC, the entire MVC API is secured using a scope as you can see in `ResourceServerConfig`

A scope of `beancounter` basically means "authorized"; You have to be authorized to call the API.

In KeyCloak, it is generally more efficient assign roles to groups and then put users into groups.

By default `spring-security-test` supports OAuth `scope`. You can't call `hasRole` to check a scope or `hasScope` to
check a role. While there
is [no difference](https://stackoverflow.com/questions/19525380/difference-between-role-and-grantedauthority-in-spring-security)
between a `SCOPE_` and a `ROLE_` except for the prefix, there is if you use the wrong annotation. You can see this by
setting a breakpoint on `SecurityExpressionRoot.hasAnyAuthorityName`

`JwtRoleConverter` is responsible for merging scope and roles into a `GrantedAuthority` set allowing us to check by ROLE
or SCOPE.

A controller can be simply secured with the `@PreAuthorize` annotation, or `@Secured` if you prefer

```java
  @RestController
  static class SimpleController {
    @GetMapping("/hello")
    @PreAuthorize("hasRole('user')")
    String sayHello() {
      return "hello";
    }

    @GetMapping("/what")
    @PreAuthorize("hasRole('no-one')")
    String sayWhat() {
      return "no one can call this";
    }
}
```

To unit test with Security

```kotlin
@SpringBootTest
@AutoConfigureMockAuth
class SomeServiceTest {

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Test
    fun do_SomethingSecured() {
    // Simple class that contains a userId 
    val systemUser = SystemUser(id = "test-user")
    val token = mockAuthConfig.login(systemUser)
    assertThat(token)
        .isNotNull
        .hasFieldOrPropertyWithValue("subject", systemUser.id)
    // Create the entry in the BC test DB
    systemUserService.register(token)
    // someService.doSomeSecuredCall()
    // ...
    //    systemUserService.getActiveUser()    

  }
}
```  

### Further reading and  references

There's a ton of information on the internet and I recommend reading the following links if you want to dive deeper. All
of these links have provided useful information which I believe I've consolidated into this module

<https://docs.spring.io/spring-security/site/docs/current/reference/html5/>

<https://blog.jdriven.com/2019/10/spring-security-5-2-oauth-2-exploration-part1/>

<https://stackoverflow.com/questions/58205510/spring-security-mapping-oauth2-claims-with-roles-to-secure-resource-server-endp>

<https://www.keycloak.org/docs/latest/authorization_services/>

#### Decrypt  tokens

<https://jwt.io/>

#### Your token is not application state

<https://paragonie.com/blog/2017/03/jwt-json-web-tokens-is-bad-standard-that-everyone-should-avoid>
