package com.beancounter.shell.integ;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.client.config.ClientConfig;
import com.beancounter.client.ingest.TrnAdapter;
import com.beancounter.client.sharesight.ShareSightConfig;
import com.beancounter.client.sharesight.ShareSightFactory;
import com.beancounter.common.input.TrustedTrnRequest;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.PortfolioUtils;
import com.beancounter.shell.kafka.KafkaTrnProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@EmbeddedKafka(
    partitions = 1,
    topics = {"topicTrnCsv"},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers",
    brokerProperties = {
        "log.dir=./kafka",
        "listeners=PLAINTEXT://localhost:${kafka.broker.port}",
        "auto.create.topics.enable=true"}
)
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {
    KafkaTrnProducer.class,
    ShareSightConfig.class,
    ClientConfig.class,
    KafkaAutoConfiguration.class
})
@ActiveProfiles("kafka")
@Slf4j
public class TrnCsvKafka {

  @Autowired
  private EmbeddedKafkaBroker embeddedKafkaBroker;

  @Autowired
  private KafkaTrnProducer kafkaTrnProducer;

  @MockBean
  private ShareSightFactory shareSightFactory;

  private List<String> row = new ArrayList<>();
  private Consumer<String, String> consumer;

  @BeforeEach
  void mockBeans() {
    log.debug("!!! {}", embeddedKafkaBroker.getBrokersAsString());
    TrnAdapter trnAdapter = Mockito.mock(TrnAdapter.class);
    Mockito.when(trnAdapter.resolveAsset(row))
        .thenReturn(AssetUtils.getAsset("ABC", "ABC"));
    row.add("ABC");
    Mockito.when(shareSightFactory.adapter(row)).thenReturn(trnAdapter);
    Map<String, Object> consumerProps =
        KafkaTestUtils.consumerProps("shell-test", "false", embeddedKafkaBroker);
    consumerProps.put("session.timeout.ms", 6000);
    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    DefaultKafkaConsumerFactory<String, String> cf =
        new DefaultKafkaConsumerFactory<>(consumerProps);

    consumer = cf.createConsumer();

    embeddedKafkaBroker.consumeFromEmbeddedTopics(consumer, "topicTrnCsv");

  }

  @Test
  void is_TrnRequestSendingCorrectly() throws Exception {

    TrustedTrnRequest trnRequest = TrustedTrnRequest.builder()
        .row(row)
        .portfolio(PortfolioUtils.getPortfolio("TEST"))
        .build();
    kafkaTrnProducer.write(trnRequest);

    log.info("Waiting for Result");
    try {
      ConsumerRecord<String, String>
          received = KafkaTestUtils.getSingleRecord(consumer, "topicTrnCsv");
      assertThat(received.value()).isNotNull();
      assertThat(new ObjectMapper().readValue(received.value(), TrustedTrnRequest.class))
          .isEqualToComparingFieldByField(trnRequest);
    } finally {
      consumer.close();
      embeddedKafkaBroker.destroy();

    }
  }

}
