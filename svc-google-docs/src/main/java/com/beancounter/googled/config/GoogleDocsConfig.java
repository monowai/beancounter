package com.beancounter.googled.config;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Value;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.SheetsScopes;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;



/**
 * Encapsulates config props to connect with Google API and perform authentication checks.
 *
 * @author mikeh
 * @since 2019-02-08
 */
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "com.beancounter.google")
@Component
@Data
@Log4j2
public class GoogleDocsConfig {

  private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
  @Value("${api:credentials.json}")
  private String api;
  @Value("${http:8888}")
  private int port;

  /**
   * Authenticate against the Google Docs service. This could ask you to download a token.
   * @param netHttpTransport transport
   * @return credentials
   * @throws IOException file error
   */
  public Credential getCredentials(final NetHttpTransport netHttpTransport) throws IOException {
    // Load client secrets.
    try (InputStream in = new FileInputStream(api)) {
      GoogleClientSecrets clientSecrets =
          GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
      log.info("Using Temp Dir {}", System.getProperty("java.io.tmpdir"));
      // Build flow and trigger user authorization request.

      GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
          netHttpTransport,
          JSON_FACTORY,
          clientSecrets,
          Collections.singletonList(SheetsScopes.SPREADSHEETS_READONLY)
      )
          .setDataStoreFactory(new FileDataStoreFactory(
              new java.io.File(System.getProperty("java"
                  + ".io.tmpdir"))))
          .setAccessType("offline")
          .build();

      LocalServerReceiver receiver = new LocalServerReceiver.Builder()
          .setPort(port)
          .build();

      return new AuthorizationCodeInstalledApp(flow, receiver)
          .authorize("user");
    }
  }
}
