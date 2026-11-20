package com.beancounter.marketdata.payslip

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.stream.function.StreamBridge
import org.springframework.stereotype.Service

/**
 * Publishes [PayslipDue] to svc-retire using Spring Cloud Stream.
 */
@Service
class PayslipDueProducer(
    private val streamBridge: StreamBridge
) {
    @Value("\${stream.enabled:true}")
    var streamEnabled: Boolean = true

    fun send(due: PayslipDue) {
        if (!streamEnabled) {
            return
        }
        log.debug("Payslip due: {} for {}", due.recurringPayslipId, due.payDate)
        streamBridge.send(BINDING, due)
    }

    companion object {
        const val BINDING = "payslipDue-out-0"
        private val log = LoggerFactory.getLogger(PayslipDueProducer::class.java)
    }
}