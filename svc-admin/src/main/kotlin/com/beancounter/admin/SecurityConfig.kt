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
 * Today: form-login backed by `spring.security.user.{name,password}` (Spring Boot
 * default user). Suitable for behind-VPN / cluster-internal access; secrets land
 * via env vars in bc-deploy.
 *
 * TODO(SBA-OIDC): swap to Auth0 OIDC login once the "BC Admin Server" Regular Web
 *   App is registered in the `beancounter.eu.auth0.com` tenant. Roughly:
 *     - add `spring.security.oauth2.client.registration.auth0.{client-id,client-secret,scope}`
 *     - replace `formLogin` below with `oauth2Login(withDefaults())`
 *     - add a `GrantedAuthoritiesMapper` that requires the `beancounter:admin`
 *       claim (the same claim the bc-view admin hub gates on).
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
                    .requestMatchers(AntPathRequestMatcher("$contextPath/login"))
                    .permitAll()
                    .requestMatchers(AntPathRequestMatcher("$contextPath/actuator/health/**"))
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.formLogin { form ->
                form
                    .loginPage("$contextPath/login")
                    .successHandler(successHandler)
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
        return http.build()
    }
}