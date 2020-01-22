package com.beancounter.shell;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.shell.config.GoogleAuthConfig;
import com.beancounter.shell.reader.GoogleTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {GoogleTransport.class, GoogleAuthConfig.class})
class TestGoogleSheet {
  @Autowired
  private GoogleTransport googleTransport;

  @Test
  void is_TransportInitialised() {
    assertThat(googleTransport.getHttpTransport()).isNotNull();
  }
}
