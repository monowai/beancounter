package com.beancounter.event.integration

import com.beancounter.common.input.TrustedTrnEvent
import org.slf4j.LoggerFactory
import org.springframework.cloud.stream.function.StreamBridge
import org.springframework.stereotype.Service

/**
 * Publish notification of a corporate action transaction affecting a portfolio
 */
@Service
class EventPublisher(
    private val streamBridge: StreamBridge
) {
    fun send(trnEvent: TrustedTrnEvent) {
        log.trace("Publishing transaction event: {}", trnEvent)
        streamBridge.send("transactionEvent-out-0", trnEvent)
    }

    companion object {
        private val log = LoggerFactory.getLogger(EventPublisher::class.java)
    }
}