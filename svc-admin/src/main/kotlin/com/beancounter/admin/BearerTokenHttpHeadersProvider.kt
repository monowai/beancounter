package com.beancounter.admin

import de.codecentric.boot.admin.server.domain.entities.Instance
import de.codecentric.boot.admin.server.web.client.HttpHeadersProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component

/**
 * Attaches a bearer token to every outbound call SBA makes to a registered
 * service's actuator. The BC services gate the actuator endpoints on
 * `SCOPE_beancounter:admin` or `SCOPE_beancounter:system` (see
 * `jar-auth/WebAuthFilterConfig.kt`), so without this header the SBA dashboard
 * shows every instance as `OFFLINE`.
 *
 * Today: a static token sourced from `BC_ADMIN_M2M_TOKEN`. Caller refreshes the
 * token out-of-band (or restarts the pod). Good enough for v1.
 *
 * TODO(SBA-M2M): wire `jar-auth`'s M2M token cache here so the provider
 *   transparently fetches + refreshes via Auth0 client_credentials. Fields
 *   needed (env-injected): client-id, client-secret, audience, issuer.
 */
@Component
class BearerTokenHttpHeadersProvider(
    @Value("\${beancounter.admin.client.bearer-token:}")
    private val bearerToken: String
) : HttpHeadersProvider {
    override fun getHeaders(instance: Instance): HttpHeaders {
        val headers = HttpHeaders()
        if (bearerToken.isNotBlank()) {
            headers.setBearerAuth(bearerToken)
        }
        return headers
    }
}