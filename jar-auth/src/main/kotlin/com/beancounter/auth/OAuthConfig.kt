package com.beancounter.auth

import com.nimbusds.jose.jwk.source.RemoteJWKSet
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.util.DefaultResourceRetriever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.cache.support.NoOpCache
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import java.net.URI
import java.time.Duration
import java.util.Objects

/**
 * Configuration to integrate with Auth0.  This config has eager initialization, so you might want to
 * mock this out in Unit Tests:
 *
 * @see com.beancounter.auth.MockAuthConfig
 */
@Configuration
@ConditionalOnProperty(
    value = ["auth.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
@Import(AuthConfig::class)
class OAuthConfig(
    @Autowired(required = false) val cacheManager: CacheManager?
) {
    private val tokenCache = "jwt.token"

    fun getTokenCache(): Cache {
        return cacheManager?.getCache(tokenCache) ?: return NoOpCache(tokenCache)
    }

    @Bean
    fun jwtDecoder(authConfig: AuthConfig): JwtDecoder {
        val jwtRestOperations =
            RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(60))
                .setReadTimeout(Duration.ofSeconds(60))
                .build()

        // JwtUtil is  a copy and paste of JwtDecoderProviderConfigurationUtils in order to be able to
        // call a couple of functions. The Spring class is inconveniently package protected.
        val configuration = JwtUtil.getConfigurationForIssuerLocation(authConfig.issuer)
        val jwkSetUri = configuration["jwks_uri"].toString()
        val jwkSource =
            RemoteJWKSet<SecurityContext>(
                URI.create(jwkSetUri).toURL(),
                DefaultResourceRetriever()
            )
        val jwtDecoder =
            NimbusJwtDecoder
                .withJwkSetUri(jwkSetUri)
                .jwsAlgorithms { algos: MutableSet<SignatureAlgorithm?> ->
                    algos.addAll(
                        JwtUtil.getSignatureAlgorithms(jwkSource)
                    )
                }.restOperations(jwtRestOperations)
                .cache(Objects.requireNonNull(getTokenCache()))
                .build()
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
        val error =
            OAuth2Error(
                "invalid_token",
                "The required audience is missing",
                null
            )

        override fun validate(jwt: Jwt): OAuth2TokenValidatorResult =
            if (jwt.audience.contains(audience)) {
                OAuth2TokenValidatorResult.success()
            } else {
                OAuth2TokenValidatorResult.failure(error)
            }
    }
}