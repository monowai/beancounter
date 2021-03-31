package com.beancounter.auth.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
/**
 * Standard OAuth 2 response to a login request
 */
class OAuth2Response(
    @JsonProperty("access_token") var token: String?,
    @JsonProperty("expires_in") var expiry: Long,
    @JsonProperty("refresh_token") var refreshToken: String?,
    @JsonProperty("token_type") var type: String?,
)
