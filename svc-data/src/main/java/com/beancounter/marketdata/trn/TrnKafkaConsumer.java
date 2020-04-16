package com.beancounter.marketdata.trn;

import com.beancounter.client.ingest.FxTransactions;
import com.beancounter.client.ingest.RowAdapter;
import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.contracts.TrnRequest;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.input.TrustedTrnRequest;
import com.beancounter.marketdata.portfolio.PortfolioService;
import com.beancounter.marketdata.service.FxRateService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.KafkaListenerErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(value = "kafka.enabled", matchIfMissing = true)
@Slf4j
public class TrnKafkaConsumer {

  @Value("${beancounter.topics.trn.csv:bc-trn-csv-dev}")
  public String topicTrnCsv;
  private RowAdapter rowAdapter;
  private FxTransactions fxTransactions;
  private TrnService trnService;
  private FxRateService fxRateService;
  private PortfolioService portfolioService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  void setFxTransactions(FxTransactions fxTransactions) {
    this.fxTransactions = fxTransactions;
  }

  @Autowired
  void setTrnService(TrnService trnService) {
    this.trnService = trnService;
  }

  @Autowired
  void setFxRateService(FxRateService fxRateService) {
    this.fxRateService = fxRateService;
  }

  @Autowired
  void setPortfolioService(PortfolioService portfolioService) {
    this.portfolioService = portfolioService;
  }

  @Autowired
  public void setRowAdapter(RowAdapter rowAdapter) {
    this.rowAdapter = rowAdapter;
  }

  @Bean
  public NewTopic topicTrnCvs() {
    return new NewTopic(topicTrnCsv, 1, (short) 1);
  }

  @Bean
  public KafkaListenerErrorHandler trnErrorHandler() {
    return (m, e) -> {
      log.error("{}. {}. {}",
          e.getMostSpecificCause().getMessage(),
          findBcCause(e),
          m.getPayload());
      return null;
    };
  }

  private String findBcCause(ListenerExecutionFailedException e) {
    StackTraceElement[] stackTrace = e.getMostSpecificCause().getStackTrace();
    for (StackTraceElement stackTraceElement : stackTrace) {
      if (stackTraceElement.getClassName().contains("com.beancounter")) {
        return stackTraceElement.toString();
      }
    }
    return "No BC Classes Found";
  }

  @Bean
  public String topicName() {
    log.info("Topic: TRN-CSV set to {}", topicTrnCsv);
    return topicTrnCsv;
  }

  @KafkaListener(topics = "#{@topicName}", errorHandler = "trnErrorHandler")
  public TrnResponse processMessage(String message) throws JsonProcessingException {
    TrustedTrnRequest trustedRequest = objectMapper.readValue(message, TrustedTrnRequest.class);
    return processMessage(trustedRequest);

  }

  public TrnResponse processMessage(TrustedTrnRequest trustedRequest) {
    log.trace("Received Message {}", trustedRequest.toString());
    if (!portfolioService.verify(trustedRequest.getPortfolio().getId())) {
      log.debug("Portfolio no longer exists. Ignoring");
      return null;
    }
    TrnInput trnInput = rowAdapter.transform(trustedRequest);
    FxRequest fxRequest = fxTransactions.buildRequest(trustedRequest.getPortfolio(), trnInput);
    FxResponse fxResponse = fxRateService.getRates(fxRequest);
    fxTransactions.setRates(fxResponse.getData(), fxRequest, trnInput);

    TrnRequest trnRequest = TrnRequest.builder()
        .portfolioId(trustedRequest.getPortfolio().getId())
        .data(Collections.singletonList(trnInput))
        .build();

    return trnService.save(trustedRequest.getPortfolio(), trnRequest);

  }

}