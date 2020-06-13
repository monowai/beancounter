package com.beancounter.marketdata.trn;

import com.beancounter.client.ingest.FxTransactions;
import com.beancounter.client.ingest.RowAdapter;
import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.contracts.TrnRequest;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.input.TrustedTrnEvent;
import com.beancounter.common.input.TrustedTrnImportRequest;
import com.beancounter.common.model.Portfolio;
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
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(value = "kafka.enabled", matchIfMissing = true)
@Slf4j
public class TrnKafkaConsumer {

  @Value("${beancounter.topics.trn.csv:bc-trn-csv-dev}")
  public String topicTrnCsv;
  @Value("${beancounter.topics.trn.event:bc-trn-event-dev}")
  public String topicTrnEvent;

  private RowAdapter rowAdapter;
  private FxTransactions fxTransactions;
  private TrnService trnService;
  private FxRateService fxRateService;
  private PortfolioService portfolioService;
  private final ObjectMapper om = new ObjectMapper();

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
    log.info("Topic: TRN-EVENT set to {}", topicTrnCsv);
    return topicTrnEvent;
  }

  @KafkaListener(topics = "#{@trnCsvTopic}", errorHandler = "bcErrorHandler")
  public TrnResponse fromCsvImport(String message) throws JsonProcessingException {
    TrustedTrnImportRequest trustedRequest = om.readValue(message, TrustedTrnImportRequest.class);
    if (trustedRequest.getMessage() != null) {
      log.info("Portfolio {} {}",
          trustedRequest.getPortfolio().getCode(),
          trustedRequest.getMessage());
      return TrnResponse.builder().build();
    } else {
      log.trace("Received Message {}", trustedRequest.toString());
      if (isValidPortfolio(trustedRequest.getPortfolio().getId())) {
        return null;
      }
      TrnInput trnInput = rowAdapter.transform(trustedRequest);
      return writeTrn(trustedRequest.getPortfolio(), trnInput);
    }

  }

  @KafkaListener(topics = "#{@trnEventTopic}", errorHandler = "bcErrorHandler")
  public TrnResponse fromTrnRequest(String message) throws JsonProcessingException {
    TrustedTrnEvent trustedTrnEvent = om.readValue(message, TrustedTrnEvent.class);
    log.trace("Received Message {}", trustedTrnEvent.toString());
    if (isValidPortfolio(trustedTrnEvent.getPortfolio().getId())) {
      return null;
    }
    return writeTrn(trustedTrnEvent.getPortfolio(), trustedTrnEvent.getTrnInput());

  }

  private boolean isValidPortfolio(String portfolioId) {
    if (!portfolioService.verify(portfolioId)) {
      log.debug("Portfolio no longer exists. Ignoring");
      return true;
    }
    return false;
  }

  private TrnResponse writeTrn(Portfolio portfolio, TrnInput trnInput) {
    FxRequest fxRequest = fxTransactions.buildRequest(portfolio, trnInput);
    FxResponse fxResponse = fxRateService.getRates(fxRequest);
    fxTransactions.setRates(fxResponse.getData(), fxRequest, trnInput);

    TrnRequest trnRequest = TrnRequest.builder()
        .portfolioId(portfolio.getId())
        .data(Collections.singletonList(trnInput))
        .build();

    return trnService.save(portfolio, trnRequest);
  }

}