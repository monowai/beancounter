package com.beancounter.shell.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OAuth2Response {
  @JsonProperty("access_token")
  private String token;
  @JsonProperty("expires_in")
  private long expiry;
  @JsonProperty("refresh_token")
  private String refreshToken;
  @JsonProperty("token_type")
  private String type;
  private String scope;

}
