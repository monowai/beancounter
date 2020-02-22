package com.beancounter.auth;

import lombok.experimental.UtilityClass;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@UtilityClass
public class TokenHelper {
  public String getToken() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return null;
    }
    if (isTokenBased(authentication)) {
      JwtAuthenticationToken jwt = (JwtAuthenticationToken) authentication;
      return jwt.getToken().getTokenValue();
    } else {
      return "unknown";
    }
  }

  public String getBearerToken() {
    return "Bearer " + getToken();
  }

  private static boolean isTokenBased(Authentication authentication) {
    return authentication instanceof JwtAuthenticationToken;
  }

}
