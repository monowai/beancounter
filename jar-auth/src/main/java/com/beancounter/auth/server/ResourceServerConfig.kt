package com.beancounter.auth.server

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter

@Configuration
@ComponentScan("com.beancounter.auth.server")
/**
 * Spring-security config to support OAuth2/JWT for MVC endpoints
 */
class ResourceServerConfig(private val jwtRoleConverter: JwtRoleConverter) : WebSecurityConfigurerAdapter() {
    @Value("\${auth.pattern:/api/**}")
    private val authPattern: String? = null

    @Value("\${management.server.servlet.context-path:/management}")
    private val actuatorPattern: String? = null

    @Throws(Exception::class)
    override fun configure(http: HttpSecurity) {
        http.cors().and()
            .authorizeRequests() // Scope permits access to the API - basically, "caller is authorised"
            .mvcMatchers("$actuatorPattern/actuator/**").hasRole(AuthConstants.OAUTH_ADMIN)
            .mvcMatchers(authPattern).hasAuthority(AuthConstants.SCOPE_BC)
            .anyRequest().authenticated()
            .and()
            .oauth2ResourceServer()
            .jwt() // User roles are carried in the claims and used for fine grained control
            //  These roles are extracted using JwtRoleConverter from the {token}.claims.realm_access
            .jwtAuthenticationConverter(jwtRoleConverter)
    }
}
