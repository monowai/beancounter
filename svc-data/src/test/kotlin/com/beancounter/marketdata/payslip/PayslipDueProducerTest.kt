package com.beancounter.marketdata.payslip

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.cloud.stream.function.StreamBridge
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The due message goes out on its own binding, and only while the stream is enabled.
 */
class PayslipDueProducerTest {
    private val streamBridge = mock(StreamBridge::class.java)
    private val producer = PayslipDueProducer(streamBridge)
    private val due =
        PayslipDue(
            recurringPayslipId = "rp-1",
            ownerId = "su-1",
            portfolioId = "pf-1",
            payDate = LocalDate.of(2026, 12, 25),
            grossSalary = BigDecimal("10000"),
            tax = BigDecimal.ZERO,
            currency = "SGD",
            cashAssetId = "cash-1"
        )

    @Test
    fun `should send the due payslip on the payslip due binding`() {
        producer.streamEnabled = true

        producer.send(due)

        verify(streamBridge).send("payslipDue-out-0", due)
    }

    @Test
    fun `should send nothing when the stream is disabled`() {
        producer.streamEnabled = false

        producer.send(due)

        verifyNoInteractions(streamBridge)
    }
}