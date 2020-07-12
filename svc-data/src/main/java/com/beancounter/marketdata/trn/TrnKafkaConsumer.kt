package com.beancounter.marketdata.trn

import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.utils.BcJson
import com.beancounter.marketdata.config.KafkaConfig
import com.fasterxml.jackson.core.JsonProcessingException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import java.io.IOException
import javax.annotation.PostConstruct

@Service
@ConditionalOnProperty(value = ["kafka.enabled"], matchIfMissing = true)
class TrnKafkaConsumer (private val kafkaConfig: KafkaConfig) {

    private lateinit var trnImport: TrnImport
    private val om = BcJson.objectMapper

    @PostConstruct
    fun logSettings() {
        log.info("trn.csv {}, trn.event {}", kafkaConfig.topicTrnCsv, kafkaConfig.topicTrnEvent)
    }

    @Autowired
    fun setTrnImportService(trnImport: TrnImport) {
        this.trnImport = trnImport
    }

    @KafkaListener(topics = ["#{@trnCsvTopic}"], errorHandler = "bcErrorHandler")
    @Throws(IOException::class)
    fun fromCsvImport(payload: String?): TrnResponse {
        return trnImport.fromCsvImport(om.readValue(payload, TrustedTrnImportRequest::class.java))!!
    }

    @KafkaListener(topics = ["#{@trnEventTopic}"], errorHandler = "bcErrorHandler")
    @Throws(JsonProcessingException::class)
    fun fromTrnRequest(payload: String?): TrnResponse {
        return trnImport.fromTrnRequest(om.readValue(payload, TrustedTrnEvent::class.java))!!
    }

    companion object {
        private val log = LoggerFactory.getLogger(TrnKafkaConsumer::class.java)
    }
}