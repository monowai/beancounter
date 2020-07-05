package com.beancounter.marketdata.integ;

import static com.beancounter.common.utils.BcJson.getObjectMapper;
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
import com.beancounter.common.input.TrustedEventInput;
import com.beancounter.common.input.TrustedTrnImportRequest;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.SystemUser;
import com.beancounter.common.model.Trn;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.marketdata.MarketDataBoot;
import com.beancounter.marketdata.currency.CurrencyService;
import com.beancounter.marketdata.event.EventWriter;
import com.beancounter.marketdata.portfolio.PortfolioService;
import com.beancounter.marketdata.providers.PriceWriter;
import com.beancounter.marketdata.registration.SystemUserService;
import com.beancounter.marketdata.service.MarketDataService;
import com.beancounter.marketdata.trn.TrnKafkaConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

@EmbeddedKafka(
    partitions = 1,
    topics = {TestKafka.TOPIC_TRN_CSV, "topicPrice", TestKafka.TOPIC_EVENT},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers",
    brokerProperties = {
        "log.dir=./build/kafka",
        "auto.create.topics.enable=true"}
)
@SpringBootTest(classes = MarketDataBoot.class)
@ActiveProfiles("kafka")
public class TestKafka {

  public static final String TOPIC_TRN_CSV = "topicTrnCsv";
  public static final String TOPIC_EVENT = "topicEvent";
  private static final Logger log = org.slf4j.LoggerFactory.getLogger(TestKafka.class);
  private final ObjectMapper objectMapper = getObjectMapper();
  private final DateUtils dateUtils = new DateUtils();
  // Setup so that the wiring is tested
  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private EmbeddedKafkaBroker embeddedKafkaBroker;

  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private KafkaTemplate<Object, TrustedTrnImportRequest> kafkaWriter;
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
  @Autowired
  private EventWriter eventWriter;
  @Autowired
  private CurrencyService currencyService;

  @Test
  void is_StaticDataLoaded() {
    assertThat(currencyService.getCurrencies()).isNotEmpty();
    assertThat(eventWriter.getKafkaEnabled()).isTrue();
  }

  @Test
  void is_TrnRequestSentAndReceived() throws Exception {
    log.debug(embeddedKafkaBroker.getBrokersAsString());

    assertThat(currencyService.getCurrencies()).isNotEmpty();
    SystemUser owner = systemUserService.save(new SystemUser("mike"));
    Mockito.when(tokenService.getSubject()).thenReturn(owner.getId());

    // The asset has to exist
    AssetRequest assetRequest = new AssetRequest();
    assetRequest.getData().put("MSFT", AssetUtils.getAssetInput("NASDAQ", "MSFT"));
    AssetUpdateResponse assetResponse = assetService.process(assetRequest);
    assertThat(assetResponse.getData().get("MSFT")).hasFieldOrProperty("id");

    Collection<PortfolioInput> portfolios = new ArrayList<>();
    portfolios.add(new PortfolioInput("KTEST", "KTEST", "USD"));
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

    TrustedTrnImportRequest trnRequest =
        new TrustedTrnImportRequest(pfResponse.iterator().next(), row);

    Consumer<String, String> consumer = getKafkaConsumer("data-test", TOPIC_TRN_CSV);

    kafkaWriter.send(TOPIC_TRN_CSV, trnRequest);

    ConsumerRecord<String, String>
        consumerRecord = KafkaTestUtils.getSingleRecord(consumer, TOPIC_TRN_CSV);

    assertThat(consumerRecord.value()).isNotNull();

    TrustedTrnImportRequest received = objectMapper
        .readValue(consumerRecord.value(), TrustedTrnImportRequest.class);

    TrnResponse trnResponse = trnKafkaConsumer.fromCsvImport(received);

    assertThat(trnResponse).isNotNull();
    assertThat(trnResponse.getData())
        .isNotNull()
        .hasSize(1);

    Asset expectedAsset = assetResponse.getData().get("MSFT");

    for (Trn trn : trnResponse.getData()) {
      assertThat(trn.getAsset()).isEqualToComparingFieldByField(expectedAsset);
      assertThat(trn.getCallerRef()).hasFieldOrPropertyWithValue("callerId", "123");
    }
  }

  private Consumer<String, String> getKafkaConsumer(String group, String topicTrnCsv) {
    Map<String, Object> consumerProps =
        KafkaTestUtils.consumerProps(group, "false", embeddedKafkaBroker);
    consumerProps.put("session.timeout.ms", 6000);
    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    //consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
    DefaultKafkaConsumerFactory<String, String> cf =
        new DefaultKafkaConsumerFactory<>(consumerProps);

    Consumer<String, String> consumer = cf.createConsumer();
    embeddedKafkaBroker.consumeFromEmbeddedTopics(consumer, topicTrnCsv);
    return consumer;
  }

  @Test
  void is_MutualFundProcessed() {
    log.debug(embeddedKafkaBroker.getBrokersAsString());
    SystemUser owner = systemUserService.save(new SystemUser("mike"));
    Mockito.when(tokenService.getSubject()).thenReturn(owner.getId());

    // The asset has to exist
    AssetRequest assetRequest = new AssetRequest();
    assetRequest.getData().put("B784NS1", AssetUtils.getAssetInput("LON", "B784NS1"));
    AssetUpdateResponse assetResponse = assetService.process(assetRequest);
    assertThat(assetResponse.getData().get("B784NS1")).hasFieldOrProperty("id");

    Collection<PortfolioInput> portfolios = new ArrayList<>();
    portfolios.add(new PortfolioInput("MFTEST", "MFTST", "USD"));
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

    TrustedTrnImportRequest trnRequest = new TrustedTrnImportRequest(
        pfResponse.iterator().next(), row);
    Asset expectedAsset = assetResponse.getData().get("B784NS1");

    TrnResponse response = trnKafkaConsumer
        .fromCsvImport(trnRequest);
    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull().hasSize(1);
    for (Trn trn : response.getData()) {
      assertThat(trn.getAsset()).isEqualToComparingFieldByField(expectedAsset);
      assertThat(trn.getCallerRef()).hasFieldOrPropertyWithValue("callerId", "10");
    }
  }

  @Test
  void is_PricePersisted() throws Exception {
    String priceDate = "2020-04-29";
    AssetRequest assetRequest = new AssetRequest();
    assetRequest.getData().put("test", AssetUtils.getAssetInput("NASDAQ", "MSFT"));

    AssetUpdateResponse assetResult = assetService.process(assetRequest);
    Asset asset = assetResult.getData().get("test");
    assertThat(asset).isNotNull().hasFieldOrProperty("id");

    MarketData marketData = new MarketData(
        asset,
        Objects.requireNonNull(dateUtils.getDate(priceDate)));
    marketData.setVolume(10);
    marketData.setOpen(BigDecimal.TEN);
    marketData.setDividend(BigDecimal.ZERO);
    Collection<MarketData> mdCollection = new ArrayList<>();
    mdCollection.add(marketData);

    PriceResponse priceResponse = new PriceResponse(mdCollection);

    Collection<AssetInput> assets = new ArrayList<>();

    Iterable<MarketData> results = priceWriter.processMessage(
        objectMapper.writeValueAsString(priceResponse));
    for (MarketData result : results) {
      assertThat(result).hasFieldOrProperty("id");

      AssetInput assetInput = new AssetInput(asset.getMarket().getCode(), asset.getCode(), asset);
      assets.add(assetInput);
    }

    // Will be resolved over the mocked API
    assets.add(new AssetInput("NASDAQ", "APPL",
        AssetUtils.getAsset("NASDAQ", "AAPL")));

    PriceRequest priceRequest = new PriceRequest(priceDate, assets);

    // First call will persist the result in an async manner
    priceResponse = marketDataService.getPriceResponse(priceRequest);
    assertThat(priceResponse).isNotNull();
    assertThat(priceResponse.getData()).isNotEmpty().hasSize(2);

    Thread.sleep(2000);
    // Second call will retrieve from DB to assert objects are correctly hydrated
    priceResponse = marketDataService.getPriceResponse(priceRequest);
    assertThat(priceResponse).isNotNull();
    assertThat(priceResponse.getData()).isNotNull().isNotEmpty().hasSize(2);

    for (MarketData md : priceResponse.getData()) {
      assertThat(md.getAsset()).isNotNull()
          .hasFieldOrProperty("id");

      assertThat(md.getAsset().getMarket())
          // These are not used client side so should be ignored
          .hasNoNullFieldsOrPropertiesExcept("currencyId", "timezoneId", "enricher");
    }
  }

  @Test
  void is_CorporateEventDispatched() throws Exception {

    Map<String, AssetInput> data = new HashMap<>();
    data.put("a", new AssetInput("NASDAQ", "TWEE"));

    AssetUpdateResponse assetResult = assetService.process(new AssetRequest(data));
    assertThat(assetResult.getData()).hasSize(1);
    Asset asset = assetResult.getData().get("a");
    assertThat(asset.getId()).isNotNull();
    assertThat(asset.getMarket()).isNotNull();
    MarketData marketData = new MarketData(
        asset,
        Objects.requireNonNull(dateUtils.getDate("2019-12-10")));
    marketData.setSource("ALPHA");
    marketData.setDividend(new BigDecimal("2.34"));
    marketData.setSplit(new BigDecimal("1.000"));

    Consumer<String, String> consumer = getKafkaConsumer("data-event-test", TOPIC_EVENT);

    // Compare with a serialised event
    eventWriter.write(marketData);

    ConsumerRecord<String, String>
        consumerRecord = KafkaTestUtils.getSingleRecord(consumer, TOPIC_EVENT);
    assertThat(consumerRecord.value()).isNotNull();

    TrustedEventInput received = objectMapper.readValue(consumerRecord.value(),
        TrustedEventInput.class);

    assertThat(received.getData())
        .hasFieldOrPropertyWithValue("rate", marketData.getDividend())
        .hasFieldOrPropertyWithValue("assetId", asset.getId())
        .hasFieldOrProperty("recordDate");

  }

}
