package com.beancounter.client;

import static org.assertj.core.api.Assertions.assertThat;

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
@SpringBootTest(classes = ExchangeService.class)
class ExchangeAliasTest {

  @Autowired
  private ExchangeService exchangeService;

  @Test
  void is_ExchangeConfigWired() {
    assertThat(exchangeService).isNotNull();
    assertThat(exchangeService.getAliases()).isNotEmpty();

  }

}
