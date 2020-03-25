package com.beancounter.marketdata.registration;


import com.beancounter.auth.server.RoleHelper;
import com.beancounter.common.contracts.RegistrationRequest;
import com.beancounter.common.contracts.RegistrationResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
@PreAuthorize("hasRole('" + RoleHelper.OAUTH_USER + "')")
public class RegistrationController {

  private SystemUserService systemUserService;

  RegistrationController(SystemUserService systemUserService) {
    this.systemUserService = systemUserService;
  }

  @GetMapping("/me")
  RegistrationResponse getMe(final @AuthenticationPrincipal Jwt jwt) {
    return RegistrationResponse.builder().data(systemUserService.find(jwt.getSubject())).build();
  }

  @PostMapping(value = "/register")
  RegistrationResponse register(
      final @AuthenticationPrincipal Jwt jwt,
      @RequestBody(required = false) RegistrationRequest registrationRequest
  ) {
    return systemUserService.register(jwt);
  }
}
