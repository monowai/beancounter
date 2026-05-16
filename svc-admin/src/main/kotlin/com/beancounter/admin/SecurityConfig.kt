package com.beancounter.admin

import de.codecentric.boot.admin.server.config.AdminServerProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer.withDefaults
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.savedrequest.HttpSessionRequestCache
import org.springframework.security.web.util.matcher.AntPathRequestMatcher

/**
 * SBA server security.
 *
 * Reuses bc-view's Auth0 application for OIDC login — no separate
 * "BC Admin Server" app needed. Admins already carry the
 * `beancounter:admin` claim in their bc-view session; the SBA UI is gated on
 * the same claim. The Auth0 application must list this server's callback URL
 * (`https://kauri.monowai.com:30530/login/oauth2/code/auth0`) alongside
 * bc-view's existing callback.
 *
 * CSRF: `CookieCsrfTokenRepository.withHttpOnlyFalse()` writes an XSRF-TOKEN
 * cookie the SBA SPA reads + echoes back as `X-XSRF-TOKEN`. The cookie is
 * intentionally not HttpOnly — the SPA needs to read it. SBA client
 * registration POSTs to `/instances` and proxied actuator calls bypass CSRF
 * because they come from server-to-server (no browser session).
 */
@Configuration
class SecurityConfig(
    private val adminServer: AdminServerProperties
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
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
                oauth2.defaultSuccessUrl("$contextPath/", true)
                oauth2.loginProcessingUrl("$contextPath/login/oauth2/code/*")
            }.logout { logout -> logout.logoutUrl("$contextPath/logout") }
            .httpBasic(withDefaults())
            .csrf { csrf ->
                csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .ignoringRequestMatchers(
                        AntPathRequestMatcher(
                            "$contextPath/instances",
                            org.springframework.http.HttpMethod.POST
                                .name()
                        ),
                        AntPathRequestMatcher(
                            "$contextPath/instances/*",
                            org.springframework.http.HttpMethod.DELETE
                                .name()
                        ),
                        AntPathRequestMatcher("$contextPath/actuator/**")
                    )
            }.requestCache { cache -> cache.requestCache(HttpSessionRequestCache()) }

        @Suppress("UNUSED_EXPRESSION")
        successHandler
        return http.build()
    }
}