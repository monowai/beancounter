package com.beancounter.marketdata.providers;

import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.model.MarketData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(value = "kafka.enabled", matchIfMissing = true)
@Slf4j
@Transactional
public class PriceWriter {

  @Value("${beancounter.topics.price:bc-price-dev}")
  public String topicPrice;
  private PriceService priceService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  void setPriceService(PriceService priceService) {
    this.priceService = priceService;
  }

  @Bean
  public NewTopic topicPrice() {
    return new NewTopic(topicPrice, 1, (short) 1);
  }

  @Bean
  public String priceTopic() {
    log.info("Topic: TRN-CSV set to {}", topicPrice);
    return topicPrice;
  }

  @KafkaListener(topics = "#{@priceTopic}", errorHandler = "bcErrorHandler")
  public Iterable<MarketData> processMessage(String message) throws JsonProcessingException {
    PriceResponse priceResponse = objectMapper.readValue(message, PriceResponse.class);
    return processMessage(priceResponse);
  }

  public Iterable<MarketData> processMessage(PriceResponse priceResponse) {
    log.trace("Received Message {}", priceResponse.toString());
    return priceService.process(priceResponse);
  }

}