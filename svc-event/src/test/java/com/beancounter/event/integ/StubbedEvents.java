package com.beancounter.event.integ;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.input.TrustedEventInput;
import com.beancounter.common.model.CorporateEvent;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Trn;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.event.service.PositionHandler;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.test.context.ActiveProfiles;

@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = {"org.beancounter:svc-data:+:stubs:11999", "org.beancounter:svc-position:+:stubs:12999"})
@ActiveProfiles("test")
@Tag("slow")
@Slf4j
@SpringBootTest
public class StubbedEvents {
  @Autowired
  private PositionHandler positionHandler;

  @Test
  void is_DividendTransactionGenerated() {
    Portfolio portfolio = Portfolio.builder()
        .code("TEST")
        .id("TEST")
        .name("NZD Portfolio")
        .currency(Currency.builder().code("NZD").name("Dollar").symbol("$").build())
        .base(Currency.builder().code("USD").name("Dollar").symbol("$").build())
        .build();

    CorporateEvent corporateEvent = CorporateEvent.builder()
        .id("StubbedEvent")
        .source("ALPHA")
        .trnType(TrnType.DIVI)
        .asset(AssetUtils.getAsset("NYSE", "KMI"))
        .recordDate(new DateUtils().getDate("2020-05-01"))
        .rate(new BigDecimal("0.2625"))
        .build();

    TrustedEventInput event = TrustedEventInput.builder()
        .portfolio(portfolio)
        .event(corporateEvent)
        .build();
    Trn trn = positionHandler.process(event);
    assertThat(trn)
        .isNotNull()
        .hasFieldOrPropertyWithValue("quantity", new BigDecimal("80.000000"))
        .hasFieldOrPropertyWithValue("tradeAmount", new BigDecimal("14.70"))
        .hasFieldOrPropertyWithValue("tax", new BigDecimal("6.30"))
        .hasFieldOrPropertyWithValue("trnType", TrnType.DIVI)
    ;
  }
}
