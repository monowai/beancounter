package com.beancounter.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SimpleController {
  @GetMapping("/")
  public String sayHello(final @AuthenticationPrincipal Jwt jwt) {
    Authentication authentication = SecurityContextHolder.getContext()
        .getAuthentication();
    return "hello " + authentication;
  }
}
