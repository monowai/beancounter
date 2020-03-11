package com.beancounter.marketdata.registration;

import com.beancounter.auth.TokenService;
import com.beancounter.common.contracts.RegistrationResponse;
import com.beancounter.common.model.SystemUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SystemUserService {
  private SystemUserRepository systemUserRepository;
  private TokenService tokenService;

  SystemUserService(SystemUserRepository systemUserRepository, TokenService tokenService) {
    this.systemUserRepository = systemUserRepository;
    this.tokenService = tokenService;
  }

  public SystemUser save(SystemUser systemUser) {
    return systemUserRepository.save(systemUser);
  }

  public SystemUser find(String id) {
    return (systemUserRepository.findById(id).orElse(null));
  }

  public RegistrationResponse register(Jwt jwt) {
    // ToDo: Find by email
    SystemUser result = find(jwt.getSubject());
    if (result == null) {
      SystemUser systemUser = SystemUser.builder()
          .email(jwt.getClaim("email"))
          .id(jwt.getSubject())
          .build();
      result = save(systemUser);
    }
    return RegistrationResponse.builder().data(result).build();

  }

  public SystemUser getActiveUser() {
    JwtAuthenticationToken token = tokenService.getJwtToken();
    if (token == null) {
      return null;
    }
    return find(token.getToken().getSubject());
  }
}
