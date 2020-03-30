package com.beancounter.marketdata.integ;

import static com.beancounter.marketdata.trn.TrnKafka.topicTrnCsv;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.auth.common.TokenService;
import com.beancounter.client.AssetService;
import com.beancounter.client.sharesight.ShareSightTradeAdapter;
import com.beancounter.common.contracts.AssetRequest;
import com.beancounter.common.contracts.AssetUpdateResponse;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.input.PortfolioInput;
import com.beancounter.common.input.TrustedTrnRequest;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.SystemUser;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.marketdata.portfolio.PortfolioService;
import com.beancounter.marketdata.registration.SystemUserService;
import com.beancounter.marketdata.trn.TrnKafka;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@EmbeddedKafka(
    partitions = 1,
    topics = {topicTrnCsv},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers",
    brokerProperties = {
        "log.dir=./kafka",
        "auto.create.topics.enable=true"}
)
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("kafka")
@Slf4j
public class KafkaTrnReader {

  // Setup so that the wiring is tested
  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private EmbeddedKafkaBroker embeddedKafkaBroker;

  @Autowired
  private AssetService assetService;

  @Autowired
  private PortfolioService portfolioService;

  @Autowired
  private SystemUserService systemUserService;

  @MockBean
  private TokenService tokenService;

  @Autowired
  private TrnKafka trnKafka;

  @Test
  void is_TrnRequestReceived() {
    log.debug(embeddedKafkaBroker.getBrokersAsString());
    SystemUser owner = systemUserService.save(SystemUser.builder().id("mike").build());
    Mockito.when(tokenService.getSubject()).thenReturn(owner.getId());

    // The asset has to exist
    AssetRequest assetRequest = AssetRequest.builder()
        .data("MSFT", AssetUtils.getAsset("MSFT", "NASDAQ"))
        .build();
    AssetUpdateResponse assetResponse = assetService.process(assetRequest);
    assertThat(assetResponse.getData().get("MSFT")).hasFieldOrProperty("id");

    Collection<PortfolioInput> portfolios = new ArrayList<>();
    portfolios.add(PortfolioInput.builder().code("KTEST")
        .currency("USD")
        .build());
    Collection<Portfolio> pfResponse = portfolioService.save(portfolios);
    assertThat(pfResponse).isNotNull();
    assertThat(pfResponse).isNotNull().hasSize(1);

    // A CSV row
    List<String> row = new ArrayList<>();
    row.add(ShareSightTradeAdapter.market, "NAS");
    row.add(ShareSightTradeAdapter.code, "MSFT");
    row.add(ShareSightTradeAdapter.name, "Test Asset");
    row.add(ShareSightTradeAdapter.type, "BUY");
    row.add(ShareSightTradeAdapter.date, "21/01/2019");
    row.add(ShareSightTradeAdapter.quantity, BigDecimal.TEN.toString());
    row.add(ShareSightTradeAdapter.price, BigDecimal.ONE.toString());
    row.add(ShareSightTradeAdapter.brokerage, BigDecimal.ZERO.toString());
    row.add(ShareSightTradeAdapter.currency, "USD");
    row.add(ShareSightTradeAdapter.fxRate, BigDecimal.ONE.toString());
    row.add(ShareSightTradeAdapter.value, BigDecimal.TEN.toString());
    row.add(ShareSightTradeAdapter.comments, "Test Comment");

    TrustedTrnRequest trnRequest = TrustedTrnRequest.builder()
        .row(row)
        .provider("KTEST")
        .portfolio(pfResponse.iterator().next())
        .asset(assetResponse.getData().get("MSFT"))
        .build();
    TrnResponse response = trnKafka.processMessage(trnRequest);
    assertThat(response).isNotNull();
  }

}
