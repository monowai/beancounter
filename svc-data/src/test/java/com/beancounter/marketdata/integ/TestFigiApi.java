package com.beancounter.marketdata.integ;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Asset;
import com.beancounter.marketdata.assets.figi.FigiProxy;
import com.beancounter.marketdata.utils.FigiMockUtils;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.File;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test")
@Tag("slow")
public class TestFigiApi {

  private static WireMockRule figiApi;

  @Autowired
  private FigiProxy figiProxy;

  @Autowired
  void mockServices() {
    // ToDo: Figure out RandomPort + Feign.  Config issues :(
    if (figiApi == null) {
      figiApi = new WireMockRule(options().port(6666));
      figiApi.start();
    }
  }

  @Test
  void is_MsftFound() throws Exception {

    File jsonFile = new ClassPathResource("/contracts/figi/msft-result.json").getFile();
    FigiMockUtils.mock(figiApi, jsonFile, "US", "MSFT");

    Asset asset = figiProxy.find("NASDAQ", "MSFT");
    assertThat(asset)
        .hasFieldOrPropertyWithValue("name", "MICROSOFT CORP")
        .isNotNull();

  }


}
