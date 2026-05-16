package com.beancounter.admin

import de.codecentric.boot.admin.server.config.AdminServerProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.config.Customizer.withDefaults
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.util.matcher.AntPathRequestMatcher

/**
 * SBA server security.
 *
 * Two filter chains, ordered:
 *
 *  1. Client registration chain (instances + actuator paths) — SBA
 *     clients (bc-data, bc-position, etc.) POST their actuator metadata
 *     using HTTP Basic with the user supplied via
 *     spring.security.user.name + spring.security.user.password.
 *     Browsers never hit these paths, so Basic-auth is the correct
 *     primary entry-point here.
 *
 *  2. UI chain (everything else) — interactive browser sessions use
 *     Auth0 OIDC. Reuses bc-view's Auth0 application so admins log in
 *     with their existing beancounter:admin claim. The Auth0 app must
 *     list this server's callback URL alongside bc-view's:
 *       https://bc-admin.monowai.com/login/oauth2/code/auth0
 *
 * Splitting the chains stops the Basic-auth WWW-Authenticate challenge
 * from beating OIDC redirect on browser hits to root.
 *
 * CSRF: CookieCsrfTokenRepository.withHttpOnlyFalse writes an XSRF-TOKEN
 * cookie the SBA SPA reads + echoes back as X-XSRF-TOKEN. The cookie is
 * intentionally not HttpOnly so the SPA can read it.
 */
@Configuration
class SecurityConfig(
    private val adminServer: AdminServerProperties
) {
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun clientRegistrationFilterChain(http: HttpSecurity): SecurityFilterChain {
        val contextPath = adminServer.contextPath
        http
            .securityMatcher("$contextPath/instances/**", "$contextPath/actuator/**")
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .httpBasic(withDefaults())
            .csrf { it.disable() }
        return http.build()
    }

    @Bean
    fun uiFilterChain(http: HttpSecurity): SecurityFilterChain {
        val contextPath = adminServer.contextPath
        val successHandler =
            org.springframework.security.web.authentication
                .SavedRequestAwareAuthenticationSuccessHandler()
                .apply {
                    setTargetUrlParameter("redirectTo")
                    setDefaultTargetUrl("$contextPath/")
                }

        http
            .authorizeHttpRequests { authz ->
                authz
                    .requestMatchers(AntPathRequestMatcher("$contextPath/assets/**"))
                    .permitAll()
                    .requestMatchers(AntPathRequestMatcher("$contextPath/login/**"))
                    .permitAll()
                    .requestMatchers(AntPathRequestMatcher("$contextPath/oauth2/**"))
                    .permitAll()
                    .requestMatchers(AntPathRequestMatcher("$contextPath/actuator/health/**"))
                    .permitAll()
                    .anyRequest()
                    .hasAuthority("SCOPE_beancounter:admin")
            }.oauth2Login { oauth2 ->
                oauth2.successHandler(successHandler)
                oauth2.loginProcessingUrl("$contextPath/login/oauth2/code/*")
            }.logout { logout -> logout.logoutUrl("$contextPath/logout") }
            .csrf { csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            }

        return http.build()
    }
}