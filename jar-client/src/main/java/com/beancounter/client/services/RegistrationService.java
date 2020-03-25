package com.beancounter.client.services;

import com.beancounter.auth.client.TokenService;
import com.beancounter.common.contracts.RegistrationRequest;
import com.beancounter.common.contracts.RegistrationResponse;
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
    RegistrationResponse response = registrationGateway
        .register(tokenService.getBearerToken(), registrationRequest);
    if (response == null) {
      throw new UnauthorizedException("Your request was rejected. Have you logged in?");
    }
    return response.getData();
  }

  public SystemUser me() {
    RegistrationResponse result = registrationGateway.me(tokenService.getBearerToken());
    if (result == null) {
      throw new UnauthorizedException("User account is not registered");
    }
    return result.getData();
  }

  @FeignClient(name = "registrationGw",
      url = "${marketdata.url:http://localhost:9510/api}")
  public interface RegistrationGateway {
    @PostMapping(value = "/register",
        produces = {MediaType.APPLICATION_JSON_VALUE},
        consumes = {MediaType.APPLICATION_JSON_VALUE})
    RegistrationResponse register(@RequestHeader("Authorization") String bearerToken,
                                  RegistrationRequest registrationRequest);

    @GetMapping(value = "/me",
        produces = {MediaType.APPLICATION_JSON_VALUE})
    RegistrationResponse me(@RequestHeader("Authorization") String bearerToken);

  }

}
