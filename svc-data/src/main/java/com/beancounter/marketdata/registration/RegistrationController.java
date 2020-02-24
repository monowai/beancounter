package com.beancounter.marketdata.registration;


import com.beancounter.auth.OauthRoles;
import com.beancounter.common.contracts.RegistrationRequest;
import com.beancounter.common.model.SystemUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
@PreAuthorize("hasRole('" + OauthRoles.ROLE_USER + "')")
public class RegistrationController {

  private SystemUserService systemUserService;

  RegistrationController(SystemUserService systemUserService) {
    this.systemUserService = systemUserService;
  }

  @PostMapping(value = "/register")
  SystemUser register(
      final @AuthenticationPrincipal Jwt jwt, @RequestBody RegistrationRequest registrationRequest
  ) {
    return systemUserService.register(SystemUser.builder()
        .id(jwt.getSubject())
        .email(jwt.getClaim("email"))
        .build());
  }
}
