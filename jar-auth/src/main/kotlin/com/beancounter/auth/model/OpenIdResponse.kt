package com.beancounter.auth.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Standard OAuth 2 response to a login request
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class OpenIdResponse(
    @param:JsonProperty("access_token") var token: String,
    @param:JsonProperty("scope") var scope: String,
    @param:JsonProperty("expires_in") var expiry: Long,
    @param:JsonProperty("token_type") var type: String,
    @param:JsonProperty("refresh_token") var refreshToken: String? = null
)