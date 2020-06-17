package com.beancounter.marketdata.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@ConditionalOnProperty(value = "kafka.enabled", matchIfMissing = true)
@Configuration
@Slf4j
public class KafkaConfig {

  @Value("${beancounter.topics.trn.csv:bc-trn-csv-dev}")
  public String topicTrnCsv;
  @Value("${beancounter.topics.trn.event:bc-trn-event-dev}")
  public String topicTrnEvent;

  @Bean
  public NewTopic topicTrnCvs() {
    return new NewTopic(topicTrnCsv, 1, (short) 1);
  }

  @Bean
  public NewTopic topicTrnEvent() {
    return new NewTopic(topicTrnEvent, 1, (short) 1);
  }

  @Bean
  public String trnCsvTopic() {
    log.info("Topic: TRN-CSV set to {}", topicTrnCsv);
    return topicTrnCsv;
  }

  @Bean
  public String trnEventTopic() {
    log.info("Topic: TRN-EVENT set to {}", topicTrnEvent);
    return topicTrnEvent;
  }
}
