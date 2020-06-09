package com.beancounter.shell.kafka;

import com.beancounter.client.ingest.TrnAdapter;
import com.beancounter.client.sharesight.ShareSightFactory;
import com.beancounter.common.input.TrustedTrnRequest;
import com.beancounter.common.model.Asset;
import com.beancounter.shell.ingest.TrnWriter;
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
  private final KafkaTemplate<String, TrustedTrnRequest> kafkaCsvTrnProducer;

  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  public KafkaTrnProducer(KafkaTemplate<String, TrustedTrnRequest> kafkaCsvTrnProducer) {
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
  public void write(TrustedTrnRequest trustedTrnRequest) {
    TrnAdapter adapter = shareSightFactory.adapter(trustedTrnRequest.getRow());
    Asset asset = adapter.resolveAsset(trustedTrnRequest.getRow());
    if (asset == null) {
      return;
    }
    ListenableFuture<SendResult<String, TrustedTrnRequest>> result =
        kafkaCsvTrnProducer.send(topicTrnCsv, trustedTrnRequest);

    SendResult<String, TrustedTrnRequest> sendResult = result.get();

    log.trace("recordMetaData: {}", sendResult.getRecordMetadata().toString());
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
