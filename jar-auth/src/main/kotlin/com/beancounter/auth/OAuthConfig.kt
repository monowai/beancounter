package com.beancounter.auth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.util.DefaultResourceRetriever
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Configuration to integrate with Auth0.  This config has eager initialization, so you might want to
 * mock this out in Unit Tests:
 *
 */
@Configuration
@ConditionalOnBean(AuthConfig::class)
@Import(AuthConfig::class)
class OAuthConfig {
    @Bean
    fun jwtDecoder(authConfig: AuthConfig): JwtDecoder {
        // JwtUtil is a copy and paste of JwtDecoderProviderConfigurationUtils in order to be able to
        // call a couple of functions. The Spring class is inconveniently package protected.
        val configuration = JwtUtil.getConfigurationForIssuerLocation(authConfig.issuer)
        val jwkSetUri = configuration["jwks_uri"].toString()

        // Create a cached JWK source with configurable lifespan and refresh-ahead
        // Auth0's JWKS keys rarely change, so long cache duration is safe
        // Refresh-ahead ensures keys are refreshed in background before expiry
        val cacheLifespanMs = TimeUnit.HOURS.toMillis(authConfig.jwksCacheLifespanHours)
        val cacheRefreshAheadMs = TimeUnit.HOURS.toMillis(authConfig.jwksCacheRefreshAheadHours)

        // Configure HTTP timeouts for JWKS retrieval
        val connectTimeoutMs = TimeUnit.SECONDS.toMillis(authConfig.jwksConnectTimeout).toInt()
        val readTimeoutMs = TimeUnit.SECONDS.toMillis(authConfig.jwksReadTimeout).toInt()
        val resourceRetriever = DefaultResourceRetriever(connectTimeoutMs, readTimeoutMs)

        val jwkSource: JWKSource<SecurityContext> =
            JWKSourceBuilder
                .create<SecurityContext>(URI.create(jwkSetUri).toURL(), resourceRetriever)
                .cache(cacheLifespanMs, cacheRefreshAheadMs)
                .retrying(true)
                .build()

        // Get supported algorithms from the JWK source
        val jwsAlgorithms =
            JwtUtil
                .getSignatureAlgorithms(jwkSource)
                .map { JWSAlgorithm.parse(it.name) }
                .toSet()

        // Create a JWT processor with our cached JWK source
        // This ensures we use our cached source instead of Spring's default NoOpCache
        val jwtProcessor =
            DefaultJWTProcessor<SecurityContext>().apply {
                jwsKeySelector = JWSVerificationKeySelector(jwsAlgorithms, jwkSource)
            }

        val jwtDecoder = NimbusJwtDecoder(jwtProcessor)
        val audienceValidator: OAuth2TokenValidator<Jwt> = AudienceValidator(authConfig.audience)
        val withAudience: OAuth2TokenValidator<Jwt> =
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(authConfig.issuer),
                audienceValidator
            )
        jwtDecoder.setJwtValidator(withAudience)
        return jwtDecoder
    }

    internal class AudienceValidator(
        private val audience: String
    ) : OAuth2TokenValidator<Jwt> {
        override fun validate(jwt: Jwt): OAuth2TokenValidatorResult =
            if (jwt.audience.contains(audience)) {
                OAuth2TokenValidatorResult.success()
            } else {
                OAuth2TokenValidatorResult.failure(
                    OAuth2Error(
                        "invalid_token",
                        "The required audience is missing",
                        null
                    )
                )
            }
    }
}