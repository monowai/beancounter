package com.beancounter.ingest.test;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.ingest.config.ExchangeConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Verify that configured exchange aliases rules are honoured.
 *
 * @author mikeh
 * @since 2019-02-13
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = ExchangeConfig.class)
class ExchangeAliasTest {

  @Autowired
  private ExchangeConfig exchangeConfig;

  @Test
  void is_ExchangeConfigWired() {
    assertThat(exchangeConfig).isNotNull();
    assertThat(exchangeConfig.getAliases()).isNotEmpty();

  }

}
