package com.beancounter.marketdata.integ;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.auth.common.TokenService;
import com.beancounter.client.AssetService;
import com.beancounter.client.sharesight.ShareSightTradeAdapter;
import com.beancounter.common.contracts.AssetRequest;
import com.beancounter.common.contracts.AssetUpdateResponse;
import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.input.AssetInput;
import com.beancounter.common.input.PortfolioInput;
import com.beancounter.common.input.TrustedTrnRequest;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.SystemUser;
import com.beancounter.common.model.Trn;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.marketdata.portfolio.PortfolioService;
import com.beancounter.marketdata.providers.PriceWriter;
import com.beancounter.marketdata.registration.SystemUserService;
import com.beancounter.marketdata.service.MarketDataService;
import com.beancounter.marketdata.trn.TrnKafkaConsumer;
import com.beancounter.marketdata.utils.KafkaUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.listener.KafkaListenerErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@EmbeddedKafka(
    partitions = 1,
    topics = {"topicTrnCsv", "topicPrice"},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers",
    brokerProperties = {
        "log.dir=./kafka",
        "auto.create.topics.enable=true"}
)
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("kafka")
@Slf4j
public class TestKafka {

  private final ObjectMapper objectMapper = new ObjectMapper();
  // Setup so that the wiring is tested
  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private EmbeddedKafkaBroker embeddedKafkaBroker;
  @Autowired
  private MarketDataService marketDataService;
  @Autowired
  private AssetService assetService;
  @Autowired
  private PriceWriter priceWriter;
  @Autowired
  private PortfolioService portfolioService;
  @Autowired
  private SystemUserService systemUserService;
  @MockBean
  private TokenService tokenService;
  @Autowired
  private TrnKafkaConsumer trnKafkaConsumer;

  private final DateUtils dateUtils = new DateUtils();

  @SneakyThrows
  @Test
  void is_TrnRequestReceived() {
    log.debug(embeddedKafkaBroker.getBrokersAsString());
    SystemUser owner = systemUserService.save(SystemUser.builder().id("mike").build());
    Mockito.when(tokenService.getSubject()).thenReturn(owner.getId());

    // The asset has to exist
    AssetRequest assetRequest = AssetRequest.builder()
        .data("MSFT", AssetUtils.getAssetInput("NASDAQ", "MSFT"))
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
    row.add(ShareSightTradeAdapter.id, "123");
    row.add(ShareSightTradeAdapter.market, "NASDAQ");
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
        .portfolio(pfResponse.iterator().next())
        .build();

    Asset expectedAsset = assetResponse.getData().get("MSFT");

    TrnResponse response = trnKafkaConsumer.processMessage(trnRequest);
    assertThat(response.getData()).isNotNull().hasSize(1);
    for (Trn trn : response.getData()) {
      assertThat(trn.getAsset()).isEqualToComparingFieldByField(expectedAsset);
      assertThat(trn.getCallerRef()).hasFieldOrPropertyWithValue("callerId", "123");
    }
  }

  @SneakyThrows
  @Test
  void is_MutualFundProcessed() {
    log.debug(embeddedKafkaBroker.getBrokersAsString());
    SystemUser owner = systemUserService.save(SystemUser.builder().id("mike").build());
    Mockito.when(tokenService.getSubject()).thenReturn(owner.getId());

    // The asset has to exist
    AssetRequest assetRequest = AssetRequest.builder()
        .data("B784NS1", AssetUtils.getAssetInput("LON", "B784NS1"))
        .build();
    AssetUpdateResponse assetResponse = assetService.process(assetRequest);
    assertThat(assetResponse.getData().get("B784NS1")).hasFieldOrProperty("id");

    Collection<PortfolioInput> portfolios = new ArrayList<>();
    portfolios.add(PortfolioInput.builder().code("MFTEST")
        .currency("USD")
        .build());
    Collection<Portfolio> pfResponse = portfolioService.save(portfolios);
    assertThat(pfResponse).isNotNull();
    assertThat(pfResponse).isNotNull().hasSize(1);

    // A CSV row
    List<String> row = new ArrayList<>();
    row.add(ShareSightTradeAdapter.id, "10");
    row.add(ShareSightTradeAdapter.market, "LON");
    row.add(ShareSightTradeAdapter.code, "B784NS1");
    row.add(ShareSightTradeAdapter.name, "");
    row.add(ShareSightTradeAdapter.type, "BUY");
    row.add(ShareSightTradeAdapter.date, "01/01/2020");
    row.add(ShareSightTradeAdapter.quantity, "3245.936");
    row.add(ShareSightTradeAdapter.price, "2.4646");
    row.add(ShareSightTradeAdapter.brokerage, "0");
    row.add(ShareSightTradeAdapter.currency, "GBP");
    row.add(ShareSightTradeAdapter.fxRate, BigDecimal.ONE.toString());
    row.add(ShareSightTradeAdapter.value, "");
    row.add(ShareSightTradeAdapter.comments, "Test Comment");

    TrustedTrnRequest trnRequest = TrustedTrnRequest.builder()
        .row(row)
        .portfolio(pfResponse.iterator().next())
        .build();

    Asset expectedAsset = assetResponse.getData().get("B784NS1");

    TrnResponse response = trnKafkaConsumer.processMessage(trnRequest);
    assertThat(response.getData()).isNotNull().hasSize(1);
    for (Trn trn : response.getData()) {
      assertThat(trn.getAsset()).isEqualToComparingFieldByField(expectedAsset);
      assertThat(trn.getCallerRef()).hasFieldOrPropertyWithValue("callerId", "10");
    }
  }

  @Test
  @SneakyThrows
  void is_PricePersisted() {
    String priceDate = "2020-04-29";
    AssetRequest assetRequest = AssetRequest.builder()
        .data("test", AssetUtils.getAssetInput("NASDAQ", "MSFT"))
        .build();

    AssetUpdateResponse assetResult = assetService.process(assetRequest);
    Asset asset = assetResult.getData().get("test");
    assertThat(asset).isNotNull().hasFieldOrProperty("id");

    Collection<MarketData> marketData = new ArrayList<>();
    marketData.add(MarketData.builder()
        .asset(asset)
        .volume(10)
        .open(BigDecimal.TEN)
        .priceDate(dateUtils.getDate(priceDate))
        .build());

    PriceResponse priceResponse = PriceResponse.builder().data(marketData).build();

    Collection<AssetInput> assets = new ArrayList<>();

    Iterable<MarketData> results = priceWriter.processMessage(
        objectMapper.writeValueAsString(priceResponse));
    for (MarketData result : results) {
      assertThat(result).hasFieldOrProperty("id");

      AssetInput assetInput = AssetInput.builder()
          .code(asset.getCode())
          .market(asset.getMarket().getCode())
          .resolvedAsset(asset)
          .build();
      assets.add(assetInput);
    }

    // Will be resolved over the mocked API
    assets.add(AssetInput.builder()
        .code("APPL")
        .market("NASDAQ")
        .resolvedAsset(AssetUtils.getAsset("NASDAQ", "AAPL"))
        .build());

    PriceRequest priceRequest = PriceRequest.builder()
        .date(priceDate) // Mocked date - MSFT has, AAPL has no price
        .assets(assets)
        .build();

    // First call will persist the result in an async manner
    priceResponse = marketDataService.getPriceResponse(priceRequest);
    assertThat(priceResponse).isNotNull();
    assertThat(priceResponse.getData()).isNotEmpty().hasSize(2);

    Thread.sleep(2000);
    // Second call will retrieve from DB to assert objects are correctly hydrated
    priceResponse = marketDataService.getPriceResponse(priceRequest);
    assertThat(priceResponse).isNotNull();
    assertThat(priceResponse.getData()).isNotEmpty().hasSize(2);
    for (MarketData md : priceResponse.getData()) {
      assertThat(md.getAsset()).isNotNull()
          .hasFieldOrProperty("id");
      assertThat(md.getAsset().getMarket())
          // These are not used client side so should be ignored
          .hasNoNullFieldsOrPropertiesExcept("currencyId", "timezoneId", "enricher");
    }
  }

  @Test
  void is_ErrorCovered() {
    KafkaUtils kafkaUtils = new KafkaUtils();
    ListenerExecutionFailedException failedException
        = new ListenerExecutionFailedException("Blah");
    String result = kafkaUtils.findBcCause(failedException);
    assertThat(result.contains("com.beancounter"));
    KafkaListenerErrorHandler handler = kafkaUtils.bcErrorHandler();
    Message<?> message = new ErrorMessage(failedException);
    Object errorResult = handler.handleError(message, failedException);
    assertThat(errorResult).isNull();
  }
}
