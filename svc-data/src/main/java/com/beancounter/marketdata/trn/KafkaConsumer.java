package com.beancounter.marketdata.trn;

import com.beancounter.client.ingest.RowAdapter;
import com.beancounter.client.services.FxTransactions;
import com.beancounter.common.contracts.TrnRequest;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.input.TrustedTrnRequest;
import java.util.Collection;
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

  @Autowired
  void setFxTransactions(FxTransactions fxTransactions) {
    this.fxTransactions = fxTransactions;
  }

  @Autowired
  void setTrnService(TrnService trnService) {
    this.trnService = trnService;
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

    TrnInput trnInput = rowAdapter.transform(
        trustedRequest.getPortfolio(),
        trustedRequest.getAsset(),
        trustedRequest.getRow(),
        trustedRequest.getProvider());

    Collection<TrnInput> trnInputs = fxTransactions.applyRates(
        trustedRequest.getPortfolio(), Collections.singleton(trnInput));

    TrnRequest trnRequest = TrnRequest.builder()
        .portfolioId(trustedRequest.getPortfolio().getId())
        .data(trnInputs)
        .build();

    trnService.save(trustedRequest.getPortfolio(), trnRequest);

  }

}