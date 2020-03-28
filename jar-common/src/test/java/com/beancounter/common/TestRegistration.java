package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.contracts.RegistrationRequest;
import com.beancounter.common.contracts.RegistrationResponse;
import com.beancounter.common.model.SystemUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

public class TestRegistration {

  private ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @SneakyThrows
  void is_RegistrationSerialization() {
    RegistrationRequest registrationRequest = RegistrationRequest.builder()
        .email("someone@somewhere.com")
        .build();
    String json = objectMapper.writeValueAsString(registrationRequest);
    assertThat(objectMapper.readValue(json, RegistrationRequest.class))
        .isEqualToComparingFieldByField(registrationRequest);
  }


  @Test
  @SneakyThrows
  void is_SystemUserSerialization() {

    SystemUser systemUser = SystemUser.builder().id(UUID.randomUUID().toString())
        .email("someone@somewhere.com")
        .build();

    String json = objectMapper.writeValueAsString(systemUser);

    assertThat(objectMapper.readValue(json, SystemUser.class))
        .isEqualToComparingFieldByField(systemUser);

    RegistrationResponse response = RegistrationResponse.builder().data(systemUser).build();

    json = objectMapper.writeValueAsString(response);
    assertThat(objectMapper.readValue(json, RegistrationResponse.class))
        .isEqualToComparingFieldByField(response);
  }

}
