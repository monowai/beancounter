package com.beancounter.shell.kafka;

import com.beancounter.client.ingest.TrnAdapter;
import com.beancounter.client.sharesight.ShareSightFactory;
import com.beancounter.common.input.TrustedTrnRequest;
import com.beancounter.common.model.Asset;
import com.beancounter.shell.ingest.IngestionRequest;
import com.beancounter.shell.ingest.TrnWriter;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Writes a trusted transaction request for processing.
 * ingest CSV KAFKA jar-shell/src/test/resources/trades.csv TEST
 */
@Service
@Slf4j
public class KafkaWriter implements TrnWriter {

  private static final String topicTrnCsv = "bc-trn-csv";
  private final KafkaTemplate<String, TrustedTrnRequest> kafkaCsvTrnProducer;
  private ShareSightFactory shareSightFactory;

  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  public KafkaWriter(KafkaTemplate<String, TrustedTrnRequest> kafkaCsvTrnProducer) {
    this.kafkaCsvTrnProducer = kafkaCsvTrnProducer;
  }

  @Autowired
  void setShareSightFactory(ShareSightFactory shareSightFactory) {
    this.shareSightFactory = shareSightFactory;
  }


  @Bean
  public NewTopic topicTrnCvs() {
    return new NewTopic(topicTrnCsv, 1, (short) 1);
  }

  @Override
  @SneakyThrows
  public void write(IngestionRequest ingestionRequest, List<String> row) {
    TrnAdapter adapter = shareSightFactory.adapter(row);
    Asset asset = adapter.resolveAsset(row);
    if (asset == null) {
      return;
    }

    TrustedTrnRequest trustedTrnRequest = TrustedTrnRequest.builder()
        .portfolio(ingestionRequest.getPortfolio())
        .asset(asset)
        .provider(ingestionRequest.getProvider())
        .row(row).build();

    kafkaCsvTrnProducer.send(topicTrnCsv, trustedTrnRequest);
  }

  @Override
  public void flush(IngestionRequest ingestionRequest) {
    //Noop
  }

  @Override
  public String id() {
    return "KAFKA";
  }


}