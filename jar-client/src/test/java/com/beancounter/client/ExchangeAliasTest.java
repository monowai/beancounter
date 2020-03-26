package com.beancounter.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.client.services.StaticService;
import com.beancounter.client.sharesight.ShareSightConfig;
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
@SpringBootTest(classes = ShareSightConfig.class)
class ExchangeAliasTest {

  @Autowired
  private StaticService staticService;

  @Test
  void is_ExchangeConfigWired() {
    assertThat(staticService).isNotNull();
    assertThat(staticService.getAliases()).isNotEmpty();

  }

  @Test
  void is_FoundForAlias() {
    assertThat(staticService.resolveAlias("NZ")).isEqualTo("NZX");
  }

}
