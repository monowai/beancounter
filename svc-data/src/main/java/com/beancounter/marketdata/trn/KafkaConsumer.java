package com.beancounter.marketdata.trn;

import com.beancounter.client.ingest.RowAdapter;
import com.beancounter.client.services.FxTransactions;
import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.contracts.TrnRequest;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.input.TrustedTrnRequest;
import com.beancounter.marketdata.service.FxService;
import java.util.Collections;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumer {
  private static final String topicTrnCsv = "bc-trn-csv";
  private RowAdapter rowAdapter;
  private FxTransactions fxTransactions;
  private TrnService trnService;
  private FxService fxService;

  @Autowired
  void setFxTransactions(FxTransactions fxTransactions) {
    this.fxTransactions = fxTransactions;
  }

  @Autowired
  void setTrnService(TrnService trnService) {
    this.trnService = trnService;
  }

  @Autowired
  void setFxService(FxService fxService) {
    this.fxService = fxService;
  }

  @Autowired
  public void setRowAdapter(RowAdapter rowAdapter) {
    this.rowAdapter = rowAdapter;
  }

  @Bean
  public NewTopic topicTrnCvs() {
    return new NewTopic(topicTrnCsv, 1, (short) 1);
  }

  @KafkaListener(topics = "bc-trn-csv")
  public void processMessage(TrustedTrnRequest trustedRequest) {

    TrnInput trnInput = rowAdapter.transform(trustedRequest);

    FxRequest fxRequest = fxTransactions.buildRequest(trustedRequest.getPortfolio(), trnInput);
    FxResponse fxResponse = fxService.getRates(fxRequest);
    fxTransactions.setRates(fxResponse.getData(), fxRequest, trnInput);

    TrnRequest trnRequest = TrnRequest.builder()
        .portfolioId(trustedRequest.getPortfolio().getId())
        .data(Collections.singletonList(trnInput))
        .build();

    trnService.save(trustedRequest.getPortfolio(), trnRequest);

  }

}