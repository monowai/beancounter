package com.beancounter.auth.server

import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.stereotype.Component
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * Mocking does not use the JwtRoleConverter configured in ResourceServerConfig, so this
 * convenience class is provided so that you might make an authenticated requests.
 *
 *
 * `MvcResult registrationResult = mockMvc.perform(
 * post("/")
 * .with(jwt(TokenUtils.getUserToken(user))
 * .authorities(new AuthorityRoleConverter(new JwtRoleConverter())))
 * ...`
 */
@Configuration
@Component
class AuthorityRoleConverter
@JvmOverloads
constructor(private val jwtRoleConverter: JwtRoleConverter = JwtRoleConverter()) :
    Converter<Jwt, Collection<GrantedAuthority>> {
    private val defaultGrantedAuthoritiesConverter = JwtGrantedAuthoritiesConverter()

    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    override fun convert(jwt: Jwt): Collection<GrantedAuthority> {
        return Stream
            .concat(
                defaultGrantedAuthoritiesConverter.convert(jwt).stream(),
                jwtRoleConverter.extractResourceRoles(jwt).stream()
            )
            .collect(Collectors.toSet())
    }
}
