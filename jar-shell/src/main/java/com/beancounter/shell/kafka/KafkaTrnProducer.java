package com.beancounter.shell.kafka;

import com.beancounter.client.ingest.TrnAdapter;
import com.beancounter.client.sharesight.ShareSightFactory;
import com.beancounter.common.input.TrustedTrnImportRequest;
import com.beancounter.common.model.Asset;
import com.beancounter.shell.ingest.TrnWriter;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;

/**
 * Writes a trusted transaction request for processing.
 * ingest CSV KAFKA jar-shell/src/test/resources/trades.csv TEST
 */
@Service
@Slf4j
public class KafkaTrnProducer implements TrnWriter {

  @Value("${beancounter.topics.trn.csv:bc-trn-csv-dev}")
  public String topicTrnCsv;
  private ShareSightFactory shareSightFactory;
  private final KafkaTemplate<String, TrustedTrnImportRequest> kafkaCsvTrnProducer;

  public KafkaTrnProducer(KafkaTemplate<String, TrustedTrnImportRequest> kafkaCsvTrnProducer) {
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
  public void reset() {
    // Not a stateful writer
  }

  @Override
  @SneakyThrows
  public void write(TrustedTrnImportRequest trustedTrnImportRequest) {
    List<String> row = trustedTrnImportRequest.getRow();
    if (row != null) {
      TrnAdapter adapter = shareSightFactory.adapter(row);
      Asset asset = adapter.resolveAsset(trustedTrnImportRequest.getRow());
      if (asset == null) {
        return;
      }
      ListenableFuture<SendResult<String, TrustedTrnImportRequest>> result =
          kafkaCsvTrnProducer.send(topicTrnCsv, trustedTrnImportRequest);

      SendResult<String, TrustedTrnImportRequest> sendResult = result.get();

      log.trace("recordMetaData: {}", sendResult.getRecordMetadata().toString());
    }
  }

  @Override
  public void flush() {
    //Noop
  }

  @Override
  public String id() {
    return "KAFKA";
  }


}
