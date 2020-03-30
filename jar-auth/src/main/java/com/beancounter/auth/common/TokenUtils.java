package com.beancounter.auth.common;

import com.beancounter.auth.server.RoleHelper;
import com.beancounter.common.model.SystemUser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * TestHelper class to generate JWT tokens you can test with.
 */
@UtilityClass
public class TokenUtils {

  public Jwt getUserToken(SystemUser systemUser) {
    return getUserToken(systemUser, getDefaultRoles());
  }

  public Jwt getUserToken(SystemUser systemUser, Map<String, Collection<String>> realmAccess) {

    return Jwt.withTokenValue(systemUser.getId())
        .header("alg", "none")
        .subject(systemUser.getId())
        .claim("email", systemUser.getEmail())
        .claim("realm_access", realmAccess)
        .claim("scope", RoleHelper.SCOPE)
        .expiresAt(new Date(System.currentTimeMillis() + 60000).toInstant())
        .build();
  }

  public Map<String, Collection<String>> getDefaultRoles() {
    return getRoles("user");
  }

  public Map<String, Collection<String>> getRoles(String... roles) {
    Map<String, Collection<String>> realmAccess = new HashMap<>();
    Collection<String> userRoles = new ArrayList<>();
    Collections.addAll(userRoles, roles);
    realmAccess.put("roles", userRoles);
    return realmAccess;

  }
}
