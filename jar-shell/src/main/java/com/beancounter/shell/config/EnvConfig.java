package com.beancounter.shell.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class EnvConfig {
  @Value("${auth.realm}")
  private String realm;
  @Value("${auth.client}")
  private String client;
  @Value("${auth.client}")
  private String uri;
  @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
  private String apiPath;
  @Value("${marketdata.url:http://localhost:9510/api}")
  private String marketDataUrl;

}
