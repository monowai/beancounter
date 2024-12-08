package com.beancounter.shell.kafka

import com.beancounter.client.sharesight.ShareSightFactory
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.shell.ingest.TrnWriter
import org.apache.kafka.clients.admin.NewTopic
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

/**
 * Writes a trusted transaction request for processing.
 * ingest CSV KAFKA jar-shell/src/test/resources/trades.csv TEST
 */
@Service
@ConditionalOnProperty(
    value = ["kafka.enabled"],
    matchIfMissing = false,
)
class KafkaTrnProducer(
    private val shareSightFactory: ShareSightFactory,
    private val kafkaCsvTrnProducer: KafkaTemplate<String, TrustedTrnImportRequest>,
) : TrnWriter {
    private val log = LoggerFactory.getLogger(KafkaTrnProducer::class.java)

    @Value("\${beancounter.topics.trn.csv:bc-trn-csv-dev}")
    private lateinit var topicTrnCsv: String

    @Bean
    fun topicTrnCvs(): NewTopic =
        NewTopic(
            topicTrnCsv,
            1,
            1.toShort(),
        )

    override fun reset() {
        // Not a stateful writer
    }

    override fun write(trnRequest: TrustedTrnImportRequest) {
        val row = trnRequest.row
        val adapter = shareSightFactory.adapter(row)
        val (_) = adapter.resolveAsset(trnRequest.row) ?: return
        val result =
            kafkaCsvTrnProducer.send(
                topicTrnCsv,
                trnRequest,
            )
        val sendResult = result.get()
        log.trace(
            "recordMetaData: {}",
            sendResult.recordMetadata.toString(),
        )
    }

    override fun flush() {
        // Noop
    }

    override fun id(): String = "KAFKA"
}
