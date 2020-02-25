package com.beancounter.marketdata.registration;

import com.beancounter.auth.AuthHelper;
import com.beancounter.common.model.SystemUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SystemUserService {
  private SystemUserRepository systemUserRepository;

  SystemUserService(SystemUserRepository systemUserRepository) {
    this.systemUserRepository = systemUserRepository;
  }

  public SystemUser save(SystemUser systemUser) {
    return systemUserRepository.save(systemUser);
  }

  public SystemUser find(String id) {
    return (systemUserRepository.findById(id).orElse(null));
  }

  public SystemUser findByEmail(String email) {
    return (systemUserRepository.findByEmail(email.toUpperCase()).orElse(null));
  }

  public SystemUser register(SystemUser systemUser) {
    // ToDo: Find by email
    SystemUser result = find(systemUser.getId());
    if (result == null) {
      result = save(systemUser);
    }
    return result;

  }

  public SystemUser getActiveUser() {
    JwtAuthenticationToken token = AuthHelper.getJwtToken();
    if (token == null) {
      return null;
    }
    return find(token.getToken().getSubject());

  }
}
