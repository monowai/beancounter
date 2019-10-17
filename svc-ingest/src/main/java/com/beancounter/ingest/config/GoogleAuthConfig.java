package com.beancounter.ingest.config;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.SheetsScopes;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
public class GoogleAuthConfig {

  private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
  private static final String TOKENS_DIRECTORY_PATH = "tokens";
  private static final List<String> SCOPES = Collections
      .singletonList(SheetsScopes.SPREADSHEETS_READONLY);
  @Value("${api.key:../secrets/google-api/credentials.json}")
  private String api;
  private int port = 8888;

  /**
   * Authenticate against the Google Docs service. This could ask you to download a token.
   *
   * @param netHttpTransport transport
   * @return credentials
   * @throws IOException file error
   */
  public Credential getCredentials(final NetHttpTransport netHttpTransport) throws IOException {
    // Load client secrets.
    log.debug("Looking for credentials at {}", getApi());
    try (InputStream in = new FileInputStream(getApi())) {
      GoogleClientSecrets clientSecrets =
          GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

      GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
          netHttpTransport, JSON_FACTORY, clientSecrets, SCOPES)
          .setAccessType("offline")
          .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
          .setAccessType("offline")
          .build();

      LocalServerReceiver receiver = new LocalServerReceiver.Builder()
          .setPort(getPort())
          .build();

      return new AuthorizationCodeInstalledApp(flow, receiver)
          .authorize("user");
    }
  }
}
