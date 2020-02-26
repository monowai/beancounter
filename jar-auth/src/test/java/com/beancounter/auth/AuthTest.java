package com.beancounter.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beancounter.common.model.SystemUser;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.util.Collection;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
    MockServletContext.class,
    TokenService.class,
    NimbusJwtDecoder.class,
    DefaultJWTProcessor.class,
    ResourceServerConfig.class})
@ImportAutoConfiguration({
    WebMvcAutoConfiguration.class})
@WebAppConfiguration
public class AuthTest {

  @Autowired
  private WebApplicationContext context;

  @Autowired
  private TokenService tokenService;

  private MockMvc mockMvc;

  private AuthorityRoleConverter roleConverter = new AuthorityRoleConverter();

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders
        .webAppContextSetup(context)
        .apply(springSecurity())
        .build();
  }

  @Test
  void are_DefaultGrantsConvertedFromToken() {
    JwtRoleConverter jwtRoleConverter = new JwtRoleConverter();
    SystemUser user = SystemUser.builder()
        .id("user")
        .build();

    Jwt token = TokenHelper.getUserToken(user);

    Collection<GrantedAuthority> defaultGrants = jwtRoleConverter.convert(token).getAuthorities();
    assertThat(defaultGrants)
        .contains(new SimpleGrantedAuthority(AppRoles.ROLE_USER))
        .contains(new SimpleGrantedAuthority(AppRoles.SCOPE_BC));
  }

  @Test
  public void has_NoTokenAndIsUnauthorized() throws Exception {
    mockMvc.perform(
        get("/hello")
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isUnauthorized())
        .andReturn();

    mockMvc.perform(
        get("/what")
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isUnauthorized())
        .andReturn();

  }

  @Test
  public void has_AuthorityToSayHelloButNotToSayWhat() throws Exception {
    SystemUser user = SystemUser.builder()
        .id("user")
        .build();

    Jwt token = TokenHelper.getUserToken(user);

    mockMvc.perform(
        get("/hello")
            .with(jwt(token).authorities(roleConverter))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andReturn();
    mockMvc.perform(
        get("/what")
            .with(jwt(token).authorities(roleConverter))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isForbidden())
        .andReturn();

  }

  @Test
  public void has_tokenButNoRoleToSayAnything() throws Exception {
    SystemUser user = SystemUser.builder()
        .id("user")
        .build();

    Jwt token = TokenHelper.getUserToken(user, TokenHelper.getRoles("blah"));

    mockMvc.perform(
        get("/hello")
            .with(jwt(token).authorities(roleConverter))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isForbidden())
        .andReturn();

    mockMvc.perform(
        get("/what")
            .with(jwt(token).authorities(roleConverter))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isForbidden())
        .andReturn();

  }

  @Test
  public void has_NoIdentityCrisis() throws Exception {
    SystemUser user = SystemUser.builder()
        .id("user")
        .build();

    Jwt token = TokenHelper.getUserToken(user);

    MvcResult result = mockMvc.perform(
        get("/me")
            .with(jwt(token).authorities(roleConverter))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andReturn();

    assertThat(result.getResponse().getContentAsString())
        .isEqualTo(user.getId());
  }

  @Test
  void is_BearerTokenBearing() {
    assertThat(tokenService.getBearerToken())
        .isEqualTo("Bearer " + tokenService.getToken());
  }

  @RestController
  static class SimpleController {
    @Autowired
    private TokenService tokenService;

    @GetMapping("/hello")
    @PreAuthorize("hasRole('" + OauthRoles.ROLE_USER + "')")
    String sayHello() {
      return "hello";
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('" + OauthRoles.ROLE_USER + "')")
    String me() {
      assert tokenService.getToken() != null;
      return Objects.requireNonNull(tokenService.getJwtToken()).getName();
    }


    @GetMapping("/what")
    @PreAuthorize("hasRole('noone')")
    String sayWhat() {
      return "no one can call this";
    }

  }

}
