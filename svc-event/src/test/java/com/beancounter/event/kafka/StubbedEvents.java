package com.beancounter.event.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.event.CorporateEvent;
import com.beancounter.common.input.TrustedEventInput;
import com.beancounter.common.input.TrustedTrnEvent;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.TrnStatus;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.event.service.EventService;
import com.beancounter.event.service.PositionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles({"kafka"})
@EmbeddedKafka(
    partitions = 1,
    topics = {StubbedEvents.TRN_EVENT, StubbedEvents.CA_EVENT},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers",
    brokerProperties = {
        "log.dir=./build/kafka",
        "auto.create.topics.enable=true"}
)
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = {
        "org.beancounter:svc-data:+:stubs:11999",
        "org.beancounter:svc-position:+:stubs:12999"}
)
@Tag("slow")
@Slf4j
@SpringBootTest
public class StubbedEvents {
  public static final String TRN_EVENT = "testTrnEvent";
  public static final String CA_EVENT = "testCaEvent";
  @Autowired
  private EventService eventService;
  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private EmbeddedKafkaBroker embeddedKafkaBroker;
  @Autowired
  private PositionService positionService;

  @Test
  void is_DividendFlowWorking() {
    CorporateEvent event = CorporateEvent.builder()
        .assetId("TEST")
        .source("ALPHA")
        .trnType(TrnType.DIVI)
        .recordDate(new DateUtils().getDate("2019-12-20"))
        .rate(new BigDecimal("2.3400"))
        .build();

    CorporateEvent saved = eventService.save(event);
    assertThat(saved.getId()).isNotNull();

    CorporateEvent second = eventService.save(event);
    assertThat(second.getId()).isEqualTo(saved.getId());

    assertThat(eventService.forAsset(event.getAssetId()))
        .isNotNull()
        .hasSize(1);

    // Is Found?
    Collection<CorporateEvent> events = eventService
        .findInRange(event.getRecordDate().minusDays(2), event.getRecordDate());
    assertThat(events).hasSize(1);
    assertThat(events.iterator().next()).isEqualToComparingFieldByField(saved);
  }

  @Test
  void is_DividendTransactionGenerated() throws JsonProcessingException {
    Map<String, Object> consumerProps =
        KafkaTestUtils.consumerProps("event-test", "false", embeddedKafkaBroker);
    consumerProps.put("session.timeout.ms", 6000);
    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    DefaultKafkaConsumerFactory<String, String> cf =
        new DefaultKafkaConsumerFactory<>(consumerProps);

    Consumer<String, String> consumer = cf.createConsumer();
    embeddedKafkaBroker.consumeFromEmbeddedTopics(consumer, TRN_EVENT);

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
        .assetId("KMI")
        .recordDate(new DateUtils().getDate("2020-05-01"))
        .rate(new BigDecimal("0.2625"))
        .build();

    TrustedEventInput event = TrustedEventInput.builder()
        .data(corporateEvent)
        .build();


    ObjectMapper om = new ObjectMapper();
    Collection<TrustedTrnEvent> trnEvents = eventService.processMessage(event);
    assertThat(trnEvents).isNotNull().hasSize(1);

    // Check the receiver gets what we send
    ConsumerRecord<String, String>
        consumerRecord = KafkaTestUtils.getSingleRecord(consumer, TRN_EVENT);

    assertThat(consumerRecord.value()).isNotNull();
    TrustedTrnEvent received = om.readValue(consumerRecord.value(), TrustedTrnEvent.class);
    TrustedTrnEvent trnEvent = trnEvents.iterator().next();
    assertThat(trnEvent)
        .isNotNull()
        .isEqualToComparingFieldByField(received)
        .hasFieldOrPropertyWithValue("portfolio", portfolio);

    assertThat(trnEvent.getTrnInput())
        .isNotNull()
        .hasFieldOrPropertyWithValue("quantity", new BigDecimal("80.000000"))
        .hasFieldOrPropertyWithValue("tradeAmount", new BigDecimal("14.70"))
        .hasFieldOrPropertyWithValue("tax", new BigDecimal("6.30"))
        .hasFieldOrPropertyWithValue("trnType", TrnType.DIVI)
        .hasFieldOrPropertyWithValue("status", TrnStatus.PROPOSED)
    ;
  }

  @Test
  void is_ZeroTotalIgnored() {

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
        .assetId("MSFT")
        .recordDate(new DateUtils().getDate("2020-05-01"))
        .rate(new BigDecimal("0.2625"))
        .build();

    TrustedTrnEvent trnEvent = positionService.process(portfolio, corporateEvent);

    assertThat(trnEvent).isNull();

  }
}
