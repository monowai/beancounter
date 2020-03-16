package com.beancounter.shell.google;

import com.beancounter.common.exception.SystemException;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.SheetsScopes;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
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
public class GoogleAuthConfig {

  private static final List<String> SCOPES = Collections
      .singletonList(SheetsScopes.SPREADSHEETS_READONLY);
  @Value("${api.path:../secrets/google-api/}")
  private String apiPath;
  @Value("${api.file:credentials.json}")
  private String apiFile;

  @Value("${api.port:8888}")
  private int port;
  private LocalServerReceiver receiver;

  @Bean
  public LocalServerReceiver receiver() {
    log.info("Callback port {}", port);
    receiver = new LocalServerReceiver.Builder()
        .setPort(getPort())
        .build();
    return receiver;
  }

  /**
   * Authenticate against the Google Docs service. This could ask you to download a token.
   *
   * @param netHttpTransport transport
   * @return credentials
   */
  public Credential getCredentials(final NetHttpTransport netHttpTransport) {
    String resolved = apiPath + (apiPath.endsWith("/") ? apiFile : "/" + apiFile);
    log.debug("Looking for credentials at {}", resolved);
    // Load client secrets.
    try (InputStream in = new FileInputStream(resolved)) {
      log.info("Reading {}", resolved);
      GoogleClientSecrets clientSecrets =
          GoogleClientSecrets.load(JacksonFactory.getDefaultInstance(), new InputStreamReader(in));

      GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
          netHttpTransport, JacksonFactory.getDefaultInstance(), clientSecrets, SCOPES)
          .setAccessType("offline")
          .setDataStoreFactory(new FileDataStoreFactory(
              new java.io.File(apiPath + "/tokens")))
          .setAccessType("offline")
          .build();

      return new AuthorizationCodeInstalledApp(flow, receiver)
          .authorize("user");
    } catch (Exception e) {
      log.error("Exception reading credentials");
      throw new SystemException(e.getMessage());
    }
  }
}
