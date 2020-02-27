package com.beancounter.client;

import com.beancounter.auth.TokenService;
import com.beancounter.common.contracts.RegistrationRequest;
import com.beancounter.common.model.SystemUser;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@Service
public class RegistrationService {
  private RegistrationGw registrationGw;
  private TokenService tokenService;

  RegistrationService(RegistrationGw registrationGw, TokenService tokenService) {
    this.registrationGw = registrationGw;
    this.tokenService = tokenService;
  }

  public SystemUser register(RegistrationRequest registrationRequest) {
    return registrationGw.register(tokenService.getBearerToken(), registrationRequest);
  }

  public SystemUser me() {
    return registrationGw.me(tokenService.getBearerToken());
  }

  @FeignClient(name = "registrationGw",
      url = "${marketdata.url:http://localhost:9510/api}")
  public interface RegistrationGw {
    @PostMapping(value = "/register",
        produces = {MediaType.APPLICATION_JSON_VALUE},
        consumes = {MediaType.APPLICATION_JSON_VALUE})
    SystemUser register(@RequestHeader("Authorization") String bearerToken,
                        RegistrationRequest registrationRequest);

    @GetMapping(value = "/me",
        produces = {MediaType.APPLICATION_JSON_VALUE})
    SystemUser me(@RequestHeader("Authorization") String bearerToken);

  }

}
