package com.beancounter.position.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Tag("slow")
class PositionBootTests {
  private WebApplicationContext context;

  @Autowired
  private PositionBootTests(WebApplicationContext webApplicationContext) {
    super();
    this.context = webApplicationContext;
  }

  @Test
  void contextLoads() {
    assertThat(context).isNotNull();
  }

}

