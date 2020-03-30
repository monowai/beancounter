package com.beancounter.auth.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

  private static boolean isTokenBased(Authentication authentication) {
    return authentication.getClass().isAssignableFrom(JwtAuthenticationToken.class);
  }

  public JwtAuthenticationToken getJwtToken() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return null;
    }
    if (isTokenBased(authentication)) {
      return (JwtAuthenticationToken) authentication;
    } else {
      return null;
    }
  }

  public String getToken() {
    JwtAuthenticationToken jwt = getJwtToken();
    if (jwt != null) {
      return jwt.getToken().getTokenValue();
    } else {
      return "unknown";
    }
  }

  public String getBearerToken() {
    return "Bearer " + getToken();
  }

  public String getSubject() {
    JwtAuthenticationToken token = getJwtToken();
    if (token == null) {
      return null;
    }
    return token.getToken().getSubject();
  }
}
