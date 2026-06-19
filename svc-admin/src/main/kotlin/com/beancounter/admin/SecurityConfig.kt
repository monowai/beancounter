package com.beancounter.admin

import de.codecentric.boot.admin.server.config.AdminServerProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.config.Customizer.withDefaults
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher

/**
 * SBA server security.
 *
 * Three filter chains, ordered:
 *
 *  0. Probe chain — K8s liveness, readiness and startup probes hit
 *     the actuator health endpoint (and its sub-paths liveness,
 *     readiness) unauthenticated. Must run before the Basic-auth
 *     chain or the kubelet receives a 401 challenge and fails the
 *     probe.
 *
 *  1. Client registration chain (instances + remaining actuator paths)
 *     — SBA clients (bc-data, bc-position, etc.) POST their actuator
 *     metadata using HTTP Basic with the user supplied via
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
 * Auth0 audience: Spring's default authorization request omits the
 * `audience` query parameter, so Auth0 issues a generic opaque token
 * with no `beancounter:*` permissions in the `scope` claim — and the
 * authorityCheck against `SCOPE_beancounter:admin` returns 403 even for
 * users with the role. [authorizationRequestResolver] injects
 * `audience=https://holdsworth.app` so Auth0 issues a JWT for the
 * Beancounter API, populating scopes from the user's RBAC permissions.
 *
 * CSRF: CookieCsrfTokenRepository.withHttpOnlyFalse writes an XSRF-TOKEN
 * cookie the SBA SPA reads + echoes back as X-XSRF-TOKEN. The cookie is
 * intentionally not HttpOnly so the SPA can read it.
 */
@Configuration
class SecurityConfig(
    private val adminServer: AdminServerProperties,
    @Value("\${auth.audience:https://holdsworth.app}")
    private val audience: String
) {
    @Bean
    fun authorizationRequestResolver(
        clientRegistrationRepository: ClientRegistrationRepository
    ): OAuth2AuthorizationRequestResolver {
        val resolver =
            DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository,
                "/oauth2/authorization"
            )
        resolver.setAuthorizationRequestCustomizer { builder ->
            builder.additionalParameters { params ->
                params["audience"] = audience
            }
        }
        return resolver
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun probeFilterChain(http: HttpSecurity): SecurityFilterChain {
        val contextPath = adminServer.contextPath
        http
            .securityMatcher(
                "$contextPath/actuator/health",
                "$contextPath/actuator/health/**",
                "$contextPath/actuator/info"
            ).authorizeHttpRequests { it.anyRequest().permitAll() }
            .csrf { it.disable() }
        return http.build()
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
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
    fun uiFilterChain(
        http: HttpSecurity,
        authorizationRequestResolver: OAuth2AuthorizationRequestResolver
    ): SecurityFilterChain {
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
                    .requestMatchers(PathPatternRequestMatcher.withDefaults().matcher("$contextPath/assets/**"))
                    .permitAll()
                    .requestMatchers(PathPatternRequestMatcher.withDefaults().matcher("$contextPath/login/**"))
                    .permitAll()
                    .requestMatchers(PathPatternRequestMatcher.withDefaults().matcher("$contextPath/oauth2/**"))
                    .permitAll()
                    .requestMatchers(PathPatternRequestMatcher.withDefaults().matcher("$contextPath/actuator/health/**"))
                    .permitAll()
                    .anyRequest()
                    .hasAuthority("SCOPE_beancounter:admin")
            }.oauth2Login { oauth2 ->
                oauth2.successHandler(successHandler)
                oauth2.loginProcessingUrl("$contextPath/login/oauth2/code/*")
                oauth2.authorizationEndpoint { endpoint ->
                    endpoint.authorizationRequestResolver(authorizationRequestResolver)
                }
            }.logout { logout -> logout.logoutUrl("$contextPath/logout") }
            .csrf { csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            }

        return http.build()
    }
}