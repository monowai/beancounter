package com.beancounter.marketdata.trn

import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Controller

/**
 * Listens to a Kafka queue for Transactions to process.
 */
@Controller
@ConditionalOnProperty(
    value = ["kafka.enabled"],
    matchIfMissing = true,
)
class TrnKafkaConsumer(
    val trnImportService: TrnImportService,
) {
    @KafkaListener(
        topics = ["#{@trnCsvTopic}"],
        errorHandler = "bcErrorHandler",
    )
    fun fromCsvImport(payload: String): TrnResponse =
        TrnResponse(
            trnImportService.fromCsvImport(
                objectMapper.readValue<TrustedTrnImportRequest>(
                    payload,
                ),
            ),
        )

    @KafkaListener(
        topics = ["#{@trnEventTopic}"],
        errorHandler = "bcErrorHandler",
    )
    fun fromTrnRequest(payload: String?): TrnResponse =
        TrnResponse(
            trnImportService.fromTrnRequest(
                objectMapper.readValue(
                    payload,
                    TrustedTrnEvent::class.java,
                ),
            ),
        )
}
