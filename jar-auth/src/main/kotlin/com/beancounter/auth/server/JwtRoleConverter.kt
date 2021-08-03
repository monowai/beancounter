package com.beancounter.auth.server

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.stereotype.Component
import java.util.Locale
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * Binds oAuth role mappings to Spring Security compatible.
 */
@Component
class JwtRoleConverter @JvmOverloads constructor(
    @Value("\${auth.realm.claim:realm_access}") private val realmClaim: String = "realm_access",
    @Value("\${auth.realm.roles:roles}") private val resourceId: String = "roles",
) : Converter<Jwt, AbstractAuthenticationToken> {

    private val defaultGrantedAuthoritiesConverter = JwtGrantedAuthoritiesConverter()

    override fun convert(jwt: Jwt) = JwtAuthenticationToken(jwt, getAuthorities(jwt))

    fun extractResourceRoles(jwt: Jwt): Collection<GrantedAuthority> {
        val resourceAccess = jwt.getClaim<Map<String, Collection<String>>>(
            realmClaim
        )
        var resourceRoles: Collection<String> = mutableListOf()
        return if (resourceAccess != null && resourceAccess[resourceId]
            .also { resourceRoles = it!! }
            != null
        ) {
            resourceRoles.stream()
                .map { role: String
                    ->
                    SimpleGrantedAuthority("ROLE_" + role.lowercase(Locale.getDefault()))
                }
                .collect(Collectors.toSet())
        } else emptySet()
    }

    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    fun getAuthorities(jwt: Jwt): Collection<GrantedAuthority> = Stream
        .concat(
            defaultGrantedAuthoritiesConverter.convert(jwt).stream(),
            extractResourceRoles(jwt).stream()
        )
        .collect(Collectors.toSet())
}
