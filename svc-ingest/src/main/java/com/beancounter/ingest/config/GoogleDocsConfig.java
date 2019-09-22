package com.beancounter.ingest.config;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Encapsulates config props to connect with Google API and perform authentication checks.
 *
 * @author mikeh
 * @since 2019-02-08
 */
@ConfigurationProperties(prefix = "beancounter.google")
@Component
@Data
@Slf4j
public class GoogleDocsConfig {

  private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
  private String api = "../secrets/google-api/credentials.json";
  private int port = 8888;

}
