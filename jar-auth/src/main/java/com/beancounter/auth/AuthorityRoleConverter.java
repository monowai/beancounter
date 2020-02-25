package com.beancounter.auth;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

/**
 * Mocking does not use the JwtRoleConverter configured in ResourceServerConfig, so this
 * convenience class is provided so that you might make an authenticated requests.
 *
 * <p>{@code MvcResult registrationResult = mockMvc.perform(
 * post("/")
 * .with(jwt(TokenHelper.getUserToken(user))
 * .authorities(new AuthorityRoleConverter(new JwtRoleConverter())))
 * ...}
 */

@Configuration
@Component
public class AuthorityRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
  private final JwtGrantedAuthoritiesConverter defaultGrantedAuthoritiesConverter
      = new JwtGrantedAuthoritiesConverter();
  private JwtRoleConverter jwtRoleConverter;

  public AuthorityRoleConverter() {
    this(new JwtRoleConverter());
  }

  public AuthorityRoleConverter(JwtRoleConverter jwtRoleConverter) {
    this.jwtRoleConverter = jwtRoleConverter;
  }

  @Override
  public Collection<GrantedAuthority> convert(@NonNull final Jwt jwt) {
    return Stream
        .concat(defaultGrantedAuthoritiesConverter.convert(jwt).stream(),
            jwtRoleConverter.extractResourceRoles(jwt).stream())
        .collect(Collectors.toSet());
  }
}
