package com.beancounter.position.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.position.PositionBoot;
import com.google.common.annotations.VisibleForTesting;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = PositionBoot.class)
@WebAppConfiguration
@ImportAutoConfiguration({FeignAutoConfiguration.class})
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.CLASSPATH,
    ids = "beancounter:svc-md:+:stubs:8090")

@ActiveProfiles("test")
@Tag("slow")
class PositionBootTests {
  private WebApplicationContext context;

  @Autowired
  private PositionBootTests(WebApplicationContext webApplicationContext) {
    super();
    this.context = webApplicationContext;
  }

  @Test
  @VisibleForTesting
  void contextLoads() {
    assertThat(context).isNotNull();
  }

}

