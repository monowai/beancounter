package com.beancounter.auth;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

@Configuration
public class ResourceServerConfig extends WebSecurityConfigurerAdapter {
  @Value("${auth.pattern:/**}")
  private String authPattern;

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    http.cors().and()
        .authorizeRequests()
        // Roles are extracted from the KeycloakRealmRoleConverter
        .mvcMatchers(authPattern).hasRole(OauthRoles.ROLE_USER)
        .anyRequest().permitAll()
        .and()
        .oauth2ResourceServer()
        .jwt()
        .jwtAuthenticationConverter(jwtAuthenticationConverter());
  }

  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    final JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
    jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
    return jwtAuthenticationConverter;
  }

  public static class KeycloakRealmRoleConverter
      implements Converter<Jwt, Collection<GrantedAuthority>> {
    public Collection<GrantedAuthority> convert(final Jwt jwt) {

      @SuppressWarnings("unchecked") final Map<String, List<String>> realmAccess =
          (Map<String, List<String>>) jwt.getClaims().get("realm_access");

      return realmAccess.get("roles").stream()
          // prefix to map to a Spring Security "ROLE"
          .map(roleName -> "ROLE_" + roleName.toUpperCase())
          .map(SimpleGrantedAuthority::new)
          .collect(Collectors.toList());
    }
  }
}