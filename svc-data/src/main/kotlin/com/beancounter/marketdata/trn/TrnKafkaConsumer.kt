package com.beancounter.marketdata.trn

import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Controller

/**
 * Listens to a Kafka queue for Transactions to process.
 */
@Controller
@ConditionalOnProperty(value = ["kafka.enabled"], matchIfMissing = true)
class TrnKafkaConsumer {
    private lateinit var trnImport: TrnImport

    @Autowired
    fun setTrnImportService(trnImport: TrnImport) {
        this.trnImport = trnImport
    }

    @KafkaListener(topics = ["#{@trnCsvTopic}"], errorHandler = "bcErrorHandler")
    fun fromCsvImport(payload: String): TrnResponse {
        return TrnResponse(
            trnImport.fromCsvImport(
                objectMapper.readValue(
                    payload,
                    TrustedTrnImportRequest::class.java,
                ),
            ),
        )
    }

    @KafkaListener(topics = ["#{@trnEventTopic}"], errorHandler = "bcErrorHandler")
    fun fromTrnRequest(payload: String?): TrnResponse {
        return TrnResponse(trnImport.fromTrnRequest(objectMapper.readValue(payload, TrustedTrnEvent::class.java)))
    }
}
