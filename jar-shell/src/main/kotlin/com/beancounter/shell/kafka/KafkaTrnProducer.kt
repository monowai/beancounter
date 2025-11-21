package com.beancounter.shell.kafka

import com.beancounter.client.sharesight.ShareSightFactory
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.shell.ingest.TrnWriter
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cloud.stream.function.StreamBridge
import org.springframework.stereotype.Service

/**
 * Writes a trusted transaction request for processing via Spring Cloud Stream.
 * ingest CSV STREAM jar-shell/src/test/resources/trades.csv TEST
 *
 * Note: This is now asynchronous. Previous Kafka implementation used .get() for blocking sends.
 */
@Service
@ConditionalOnProperty(
    value = ["stream.enabled"],
    matchIfMissing = false
)
class KafkaTrnProducer(
    private val shareSightFactory: ShareSightFactory,
    private val streamBridge: StreamBridge
) : TrnWriter {
    private val log = LoggerFactory.getLogger(KafkaTrnProducer::class.java)

    override fun reset() {
        // Not a stateful writer
    }

    override fun write(trnRequest: TrustedTrnImportRequest) {
        val row = trnRequest.row
        val adapter = shareSightFactory.adapter(row)
        val (_) = adapter.resolveAsset(trnRequest.row) ?: return

        val sent = streamBridge.send("csvImport-out-0", trnRequest)
        if (sent) {
            log.trace("Transaction import request sent successfully")
        } else {
            log.warn("Failed to send transaction import request")
        }
    }

    override fun flush() {
        // Noop
    }

    override fun id(): String = "STREAM"
}