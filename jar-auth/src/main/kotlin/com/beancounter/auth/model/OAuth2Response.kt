package com.beancounter.auth.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Standard OAuth 2 response to a login request
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class OAuth2Response(
    @JsonProperty("access_token") var token: String,
    @JsonProperty("scope") var scope: String,
    @JsonProperty("expires_in") var expiry: Long,
    @JsonProperty("token_type") var type: String,
    @JsonProperty("refresh_token") var refreshToken: String?
)
