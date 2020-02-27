package com.beancounter.client;

import com.beancounter.auth.TokenService;
import com.beancounter.common.contracts.RegistrationRequest;
import com.beancounter.common.exception.UnauthorizedException;
import com.beancounter.common.model.SystemUser;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@Service
public class RegistrationService {
  private RegistrationGateway registrationGateway;
  private TokenService tokenService;

  RegistrationService(RegistrationGateway registrationGateway, TokenService tokenService) {
    this.registrationGateway = registrationGateway;
    this.tokenService = tokenService;
  }

  public SystemUser register(RegistrationRequest registrationRequest) {
    return registrationGateway.register(tokenService.getBearerToken(), registrationRequest);
  }

  public SystemUser me() {
    SystemUser result = registrationGateway.me(tokenService.getBearerToken());
    if (result == null) {
      throw new UnauthorizedException("Not logged in");
    }
    return result;
  }

  @FeignClient(name = "registrationGw",
      url = "${marketdata.url:http://localhost:9510/api}")
  public interface RegistrationGateway {
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
