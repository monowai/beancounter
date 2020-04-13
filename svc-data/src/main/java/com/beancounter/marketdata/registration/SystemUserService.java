package com.beancounter.marketdata.registration;

import com.beancounter.auth.common.TokenService;
import com.beancounter.common.contracts.RegistrationResponse;
import com.beancounter.common.model.SystemUser;
import com.beancounter.common.utils.KeyGenUtils;
import java.util.UUID;
import javax.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Transactional
public class SystemUserService {
  private SystemUserRepository systemUserRepository;
  private TokenService tokenService;

  SystemUserService(SystemUserRepository systemUserRepository, TokenService tokenService) {
    this.systemUserRepository = systemUserRepository;
    this.tokenService = tokenService;
  }

  public SystemUser save(SystemUser systemUser) {
    if (systemUser.getId() == null) {
      systemUser.setId(KeyGenUtils.format(UUID.randomUUID()));
    }
    return systemUserRepository.save(systemUser);
  }

  public SystemUser find(String id) {
    if (id == null) {
      return null;
    }
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
    return find(tokenService.getSubject());
  }
}
