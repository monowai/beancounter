package com.beancounter.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.input.TrustedTrnEvent;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.CorporateEvent;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.QuantityValues;
import com.beancounter.common.model.TrnStatus;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.common.utils.PortfolioUtils;
import com.beancounter.event.service.alpha.AlphaEventAdapter;
import com.beancounter.event.service.alpha.AlphaEventConfig;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = AlphaEventConfig.class)
public class TestAlphaEvents {

  private static final Currency USD = Currency.builder().code("USD").build();
  @Autowired
  private AlphaEventAdapter alphaEventAdapter;

  @Test
  void is_UsDividendCalculated() {
    Market market = Market.builder()
        .code("NASDAQ")
        .currency(USD)
        .build();
    Asset asset = AssetUtils.getAsset(market, "KMI");
    DateUtils dateUtils = new DateUtils();

    CorporateEvent event = CorporateEvent.builder()
        .trnType(TrnType.DIVI)
        .source("ALPHA")
        .asset(asset)
        .recordDate(dateUtils.getDate("2020-05-01"))
        .rate(new BigDecimal("0.2625"))
        .build();

    Portfolio portfolio = PortfolioUtils.getPortfolio("TEST", USD);
    Position position = Position.builder()
        .asset(asset)
        .quantityValues(QuantityValues.builder().purchased(new BigDecimal("80")).build())
        .build();
    assertThat(position.getQuantityValues().getTotal()).isEqualTo(new BigDecimal("80"));
    TrustedTrnEvent trnEvent = alphaEventAdapter.generate(portfolio, position, event);
    assertThat(trnEvent).isNotNull();
    assertThat(trnEvent.getPortfolio()).isNotNull();

    assertThat(trnEvent.getTrnInput())
        .hasFieldOrPropertyWithValue("asset", asset.getId())
        .hasFieldOrPropertyWithValue("trnType", TrnType.DIVI)
        .hasFieldOrPropertyWithValue("status", TrnStatus.PROPOSED)
        .hasFieldOrPropertyWithValue("tradeDate", dateUtils.getDate("2020-05-19"))
        .hasFieldOrPropertyWithValue("price", event.getRate())
        .hasFieldOrPropertyWithValue("tax", new BigDecimal("6.30")) // @ 30%
        .hasFieldOrPropertyWithValue("tradeAmount", new BigDecimal("14.70"))
    ;
  }
}
