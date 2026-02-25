package com.beancounter.auth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.KeySourceException
import com.nimbusds.jose.jwk.JWKMatcher
import com.nimbusds.jose.jwk.JWKSelector
import com.nimbusds.jose.jwk.KeyType
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.util.DefaultResourceRetriever
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import org.slf4j.LoggerFactory
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
 * mock this out in Unit Tests.
 */
@Configuration
@ConditionalOnBean(AuthConfig::class)
@Import(AuthConfig::class)
class OAuthConfig {
    private val log = LoggerFactory.getLogger(OAuthConfig::class.java)

    @Bean
    fun jwtDecoder(authConfig: AuthConfig): JwtDecoder {
        // Auth0 JWKS URI follows a standard convention — derive directly
        // to avoid an OIDC discovery HTTP call with no timeout protection
        val jwkSetUri = "${authConfig.issuer}.well-known/jwks.json"
        log.info("JWKS URI: {}", jwkSetUri)

        // Create a cached JWK source with configurable lifespan and refresh-ahead
        // Auth0's JWKS keys rarely change, so long cache duration is safe
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

        // Resolve supported algorithms from the JWK source
        val jwsAlgorithms = getJwsAlgorithms(jwkSource)

        // Create a JWT processor with our cached JWK source
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

    /**
     * Resolve JWS algorithms from the JWK source keys.
     * Inspects the algorithm metadata on each key; falls back to all RSA/EC algorithms
     * when a key doesn't declare its algorithm explicitly.
     */
    private fun getJwsAlgorithms(jwkSource: JWKSource<SecurityContext>): Set<JWSAlgorithm> {
        val jwkMatcher =
            JWKMatcher
                .Builder()
                .publicOnly(true)
                .keyUses(KeyUse.SIGNATURE, null)
                .keyTypes(KeyType.RSA, KeyType.EC)
                .build()
        val algorithms: MutableSet<JWSAlgorithm> = HashSet()
        try {
            val jwks = jwkSource[JWKSelector(jwkMatcher), null]
            for (jwk in jwks) {
                if (jwk.algorithm != null) {
                    algorithms.add(JWSAlgorithm.parse(jwk.algorithm.name))
                } else if (jwk.keyType === KeyType.RSA) {
                    algorithms.addAll(JWSAlgorithm.Family.RSA)
                } else if (jwk.keyType === KeyType.EC) {
                    algorithms.addAll(JWSAlgorithm.Family.EC)
                }
            }
        } catch (ex: KeySourceException) {
            throw IllegalStateException("Failed to retrieve JWK keys", ex)
        }
        check(algorithms.isNotEmpty()) { "Failed to find any algorithms from the JWK set" }
        return algorithms
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