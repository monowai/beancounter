package com.beancounter.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.auth.common.TokenUtils;
import com.beancounter.auth.server.JwtRoleConverter;
import com.beancounter.common.model.SystemUser;
import com.beancounter.common.utils.KeyGenUtils;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class RealmTest {
  @Test
  void is_RoleConverterValid() {
    JwtRoleConverter jwtRoleConverter =
        new JwtRoleConverter("empty", "nothing");

    assertThat(jwtRoleConverter.getAuthorities(
        TokenUtils.getUserToken(
            SystemUser.builder()
                .id(KeyGenUtils.format(UUID.randomUUID()))
                .build())))
        .hasSize(3)
        .containsExactlyInAnyOrder(
            // Default scopes
            new SimpleGrantedAuthority("SCOPE_email"),
            new SimpleGrantedAuthority("SCOPE_profile"),
            new SimpleGrantedAuthority("SCOPE_beancounter"));
  }
}
