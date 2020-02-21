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
      return "Bearer " + jwt.getToken().getTokenValue();
    } else {
      return null;
    }

  }

  private static boolean isTokenBased(Authentication authentication) {
    return authentication instanceof JwtAuthenticationToken;
  }

}
