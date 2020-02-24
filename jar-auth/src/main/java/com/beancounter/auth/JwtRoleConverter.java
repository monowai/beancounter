package com.beancounter.auth;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

@Component
public final class JwtRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {
  private final JwtGrantedAuthoritiesConverter defaultGrantedAuthoritiesConverter
      = new JwtGrantedAuthoritiesConverter();
  private String resourceId;
  private String realmClaim;

  public JwtRoleConverter () {
    this ("realm_access", "roles");
  }
  public JwtRoleConverter(@Value("${auth.realm.claim:realm_access}") String realmClaim,
                          @Value("${auth.realm.roles:roles}") String resourceId) {
    this.realmClaim = realmClaim;
    this.resourceId = resourceId;
  }

  Collection<? extends GrantedAuthority> extractResourceRoles(@NonNull final Jwt jwt) {
    Map<String, Collection<String>> resourceAccess = jwt.getClaim(realmClaim);
    Collection<String> resourceRoles;
    if (resourceAccess != null && (resourceRoles = resourceAccess.get(resourceId)) != null) {
      return resourceRoles.stream()
          .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toLowerCase()))
          .collect(Collectors.toSet());
    }
    return Collections.emptySet();
  }

  public Collection<GrantedAuthority> getAuthorities(@NonNull final Jwt jwt) {
    return Stream
        .concat(defaultGrantedAuthoritiesConverter.convert(jwt).stream(),
            extractResourceRoles(jwt).stream())
        .collect(Collectors.toSet());
  }

  @Override
  public AbstractAuthenticationToken convert(@NonNull final Jwt source) {
    Collection<GrantedAuthority> authorities = getAuthorities(source);
    return new JwtAuthenticationToken(source, authorities);
  }


}
