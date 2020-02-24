package com.beancounter.auth;

import com.beancounter.common.model.SystemUser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * TestHelper class that generates JWT tokens you can test with.
 */
@UtilityClass
public class TokenHelper {

  public static final String SCOPE = "beancounter profile email";

  public Jwt getUserToken(SystemUser systemUser) {
    Collection<String> roles = new ArrayList<>();
    roles.add("user");
    Map<String, Collection<String>> realmAccess = new HashMap<>();
    realmAccess.put("roles", roles);

    return Jwt.withTokenValue("token")
        .header("alg", "none")
        .subject(systemUser.getId())
        .claim("email", systemUser.getEmail())
        .claim("realm_access", realmAccess)
        .claim("scope", SCOPE)
        .expiresAt(new Date(System.currentTimeMillis() + 60000).toInstant())
        .build();

  }
}
