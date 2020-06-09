package com.beancounter.marketdata.integ;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.client.AssetService;
import com.beancounter.common.contracts.AssetRequest;
import com.beancounter.common.contracts.AssetUpdateResponse;
import com.beancounter.common.input.AssetInput;
import com.beancounter.common.model.CorporateEvent;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.marketdata.MarketDataBoot;
import com.beancounter.marketdata.event.EventService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = MarketDataBoot.class)
@WebAppConfiguration
@ActiveProfiles("test")
public class TestEventService {

  @Autowired
  private EventService eventService;
  @Autowired
  private AssetService assetService;
  @Autowired
  private DateUtils dateUtils;

  @Test
  void is_DividendFlowWorking() {
    AssetRequest assetRequest = AssetRequest.builder()
        .data("a", AssetInput.builder()
            .code("TWEE")
            .name("No matter")
            .market("NASDAQ")
            .build())
        .build();
    AssetUpdateResponse assetResult = assetService.process(assetRequest);
    assertThat(assetResult.getData()).hasSize(1);

    CorporateEvent event = CorporateEvent.builder()
        .asset(assetResult.getData().get("a"))
        .recordDate(dateUtils.getDate("2019-12-20"))
        .rate(new BigDecimal("2.34"))
        .build();

    CorporateEvent saved = eventService.save(event);
    assertThat(saved.getId()).isNotNull();

    CorporateEvent second = eventService.save(event);
    assertThat(second.getId()).isEqualTo(saved.getId());
  }
}
