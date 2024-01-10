package com.beancounter.marketdata.event

import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.input.TrustedEventInput
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.MarketData.Companion.isDividend
import com.beancounter.common.model.MarketData.Companion.isSplit
import com.beancounter.common.model.TrnType
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

/**
 * Kafka Corporate Action/Event subscriber.
 */
@Service
@ConditionalOnProperty(value = ["kafka.enabled"], matchIfMissing = true)
class EventProducer {
    @Value("\${kafka.enabled:true}")
    var kafkaEnabled: Boolean = false

    @Value("\${beancounter.topics.ca.event:bc-ca-event-dev}")
    private lateinit var topicEvent: String
    private lateinit var kafkaCaProducer: KafkaTemplate<String, TrustedEventInput>

    @PostConstruct
    fun logConfig() {
        log.info("BEANCOUNTER_TOPICS_CA_EVENT: {}", topicEvent)
    }

    @Autowired
    fun setKafkaCaProducer(kafkaCaProducer: KafkaTemplate<String, TrustedEventInput>) {
        this.kafkaCaProducer = kafkaCaProducer
    }

    fun write(marketData: MarketData) {
        if (!kafkaEnabled || !isValidEvent(marketData)) {
            return
        }
        val corporateEvent =
            CorporateEvent(
                id = null,
                trnType = TrnType.DIVI,
                source = marketData.source,
                recordDate = marketData.priceDate!!,
                assetId = marketData.asset.id,
                rate = marketData.dividend,
                split = marketData.split,
            )
        log.trace("Dispatch {} ... {}", topicEvent, marketData)
        kafkaCaProducer.send(topicEvent, TrustedEventInput(corporateEvent))
    }

    private fun isValidEvent(marketData: MarketData?): Boolean {
        return if (marketData == null) {
            false
        } else {
            isSplit(marketData) || isDividend(marketData)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(EventProducer::class.java)
    }
}
