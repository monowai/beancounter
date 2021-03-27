package com.beancounter.auth.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OAuth2Response {
  @JsonProperty("access_token")
  String token;
  @JsonProperty("expires_in")
  long expiry;
  @JsonProperty("refresh_token")
  String refreshToken;
  @JsonProperty("token_type")
  String type;
  String scope;

}
