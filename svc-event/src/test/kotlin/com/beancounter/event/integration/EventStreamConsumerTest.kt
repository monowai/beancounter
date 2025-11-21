package com.beancounter.event.integration

import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.input.TrustedEventInput
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.event.service.EventService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.math.BigDecimal

/**
 * Unit test for EventStreamConsumer.
 * Tests the eventProcessor function bean logic directly.
 */
class EventStreamConsumerTest {
    @Test
    fun `eventProcessor should call eventService process`() {
        // Given
        val eventService = mock(EventService::class.java)
        val consumer = EventStreamConsumer(eventService)

        val corporateEvent =
            CorporateEvent(
                id = "test-event",
                trnType = TrnType.DIVI,
                recordDate = DateUtils().getFormattedDate("2020-05-01"),
                source = "ALPHA",
                assetId = "MSFT",
                rate = BigDecimal("0.2625")
            )
        val eventInput = TrustedEventInput(corporateEvent)

        // When
        consumer.eventProcessor().accept(eventInput)

        // Then
        verify(eventService).process(eventInput)
    }
}