package com.beancounter.auth

import com.beancounter.common.exception.SystemException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.KeySourceException
import com.nimbusds.jose.jwk.JWKMatcher
import com.nimbusds.jose.jwk.JWKSelector
import com.nimbusds.jose.jwk.KeyType
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.RequestEntity
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.util.Assert
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

/**
 *
 * Copy/paste/kotlin version of JwtDecoderProviderConfigurationUtils in order to be able to
 * call a couple of functions. The Spring class is inconveniently package protected.
 *
 * Allows resolving configuration from an [OpenID
 * Provider Configuration](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderConfig) or
 * [Authorization Server Metadata
 * Request](https://tools.ietf.org/html/rfc8414#section-3.1) based on provided issuer and method invoked.
 *
 * @author Thomas Vitale
 * @author Rafiullah Hamedy
 * @since 5.2
 */
internal object JwtUtil {
    private const val OIDC_METADATA_PATH = "/.well-known/openid-configuration"
    private const val OAUTH_METADATA_PATH = "/.well-known/oauth-authorization-server"
    private val rest = RestTemplate()
    private val STRING_OBJECT_MAP: ParameterizedTypeReference<Map<String, Any>?> =
        object : ParameterizedTypeReference<Map<String, Any>?>() {}

    fun getConfigurationForIssuerLocation(issuer: String): Map<String, Any> {
        val uri = URI.create(issuer)
        return getConfiguration(issuer, oidc(uri), oidcRfc8414(uri), oauth(uri))
    }

    fun getSignatureAlgorithms(jwkSource: JWKSource<SecurityContext>): Set<SignatureAlgorithm> {
        val jwkMatcher = JWKMatcher.Builder().publicOnly(true).keyUses(KeyUse.SIGNATURE, null)
            .keyTypes(KeyType.RSA, KeyType.EC).build()
        val jwsAlgorithms: MutableSet<JWSAlgorithm> = HashSet()
        try {
            val jwks = jwkSource[JWKSelector(jwkMatcher), null]
            for (jwk in jwks) {
                if (jwk.algorithm != null) {
                    val jwsAlgorithm = JWSAlgorithm.parse(jwk.algorithm.name)
                    jwsAlgorithms.add(jwsAlgorithm)
                } else {
                    if (jwk.keyType === KeyType.RSA) {
                        jwsAlgorithms.addAll(JWSAlgorithm.Family.RSA)
                    } else if (jwk.keyType === KeyType.EC) {
                        jwsAlgorithms.addAll(JWSAlgorithm.Family.EC)
                    }
                }
            }
        } catch (ex: KeySourceException) {
            throw IllegalStateException(ex)
        }
        val signatureAlgorithms: MutableSet<SignatureAlgorithm> = HashSet()
        for (jwsAlgorithm in jwsAlgorithms) {
            val signatureAlgorithm = SignatureAlgorithm.from(jwsAlgorithm.name)
            if (signatureAlgorithm != null) {
                signatureAlgorithms.add(signatureAlgorithm)
            }
        }
        Assert.notEmpty(signatureAlgorithms, "Failed to find any algorithms from the JWK set")
        return signatureAlgorithms
    }

    private fun getConfiguration(issuer: String, vararg uris: URI): Map<String, Any> {
        val errorMessage = "Unable to resolve the Configuration with the provided Issuer of \"$issuer\""
        for (uri in uris) {
            try {
                val request = RequestEntity.get(uri).build()
                val response = rest.exchange(request, STRING_OBJECT_MAP)
                val configuration = response.body ?: throw SystemException("Unable to obtain JWT Config")
                Assert.isTrue(configuration["jwks_uri"] != null, "The public JWK set URI must not be null")
                return configuration
            } catch (ex: RuntimeException) {
                if (!(
                        ex is HttpClientErrorException &&
                            ex.statusCode.is4xxClientError
                        )
                ) {
                    throw IllegalArgumentException(errorMessage, ex)
                }
                // else try another endpoint
            }
        }
        throw IllegalArgumentException(errorMessage)
    }

    private fun oidc(issuer: URI): URI {
        // @formatter:off
        return UriComponentsBuilder.fromUri(issuer)
            .replacePath(issuer.path + OIDC_METADATA_PATH)
            .build(emptyMap<String, Any>())
        // @formatter:on
    }

    private fun oidcRfc8414(issuer: URI): URI {
        // @formatter:off
        return UriComponentsBuilder.fromUri(issuer)
            .replacePath(OIDC_METADATA_PATH + issuer.path)
            .build(emptyMap<String, Any>())
        // @formatter:on
    }

    private fun oauth(issuer: URI): URI {
        // @formatter:off
        return UriComponentsBuilder.fromUri(issuer)
            .replacePath(OAUTH_METADATA_PATH + issuer.path)
            .build(emptyMap<String, Any>())
        // @formatter:on
    }
}
