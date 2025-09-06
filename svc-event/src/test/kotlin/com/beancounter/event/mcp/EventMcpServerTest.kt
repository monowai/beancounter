package com.beancounter.event.mcp

import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.model.TrnType
import com.beancounter.event.contract.CorporateEventResponse
import com.beancounter.event.contract.CorporateEventResponses
import com.beancounter.event.service.BackFillService
import com.beancounter.event.service.EventLoader
import com.beancounter.event.service.EventService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDate

@SpringBootTest(classes = [EventMcpServer::class])
@ActiveProfiles("test")
class EventMcpServerTest {
    @MockitoBean
    private lateinit var eventService: EventService

    @MockitoBean
    private lateinit var eventLoader: EventLoader

    @MockitoBean
    private lateinit var backFillService: BackFillService

    private lateinit var eventMcpServer: EventMcpServer

    private val testEvent =
        CorporateEvent(
            id = "test-event-1",
            trnType = TrnType.DIVI,
            assetId = "test-asset-1",
            recordDate = LocalDate.of(2024, 1, 15),
            payDate = LocalDate.of(2024, 1, 20),
            rate = 0.50.toBigDecimal()
        )

    private val testEvents =
        listOf(
            testEvent,
            CorporateEvent(
                id = "test-event-2",
                trnType = TrnType.SPLIT,
                assetId = "test-asset-2",
                recordDate = LocalDate.of(2024, 1, 10),
                split = 2.0.toBigDecimal()
            )
        )

    @BeforeEach
    fun setUp() {
        eventMcpServer = EventMcpServer(eventService, eventLoader, backFillService)
    }

    @Test
    fun `should get event by ID successfully`() {
        // Given
        val expectedResponse = CorporateEventResponse(testEvent)
        whenever(eventService["test-event-1"]).thenReturn(expectedResponse)

        // When
        val result = eventMcpServer.getEvent("test-event-1")

        // Then
        assertEquals(expectedResponse, result)
        assertEquals(testEvent, result.data)
        verify(eventService)["test-event-1"]
    }

    @Test
    fun `should get asset events successfully`() {
        // Given
        val expectedResponse = CorporateEventResponses(testEvents)
        whenever(eventService.getAssetEvents("test-asset-1")).thenReturn(expectedResponse)

        // When
        val result = eventMcpServer.getAssetEvents("test-asset-1")

        // Then
        assertEquals(expectedResponse, result)
        assertEquals(testEvents, result.data)
        verify(eventService).getAssetEvents("test-asset-1")
    }

    @Test
    fun `should get events in date range successfully`() {
        // Given
        val startDate = "2024-01-01"
        val endDate = "2024-01-31"
        whenever(eventService.findInRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)))
            .thenReturn(testEvents)

        // When
        val result = eventMcpServer.getEventsInDateRange(startDate, endDate)

        // Then
        assertEquals(testEvents, result.data)
        verify(eventService).findInRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31))
    }

    @Test
    fun `should get scheduled events successfully`() {
        // Given
        val startDate = "2024-01-01"
        val expectedResponse = CorporateEventResponses(testEvents)
        whenever(eventService.getScheduledEvents(LocalDate.of(2024, 1, 1))).thenReturn(expectedResponse)

        // When
        val result = eventMcpServer.getScheduledEvents(startDate)

        // Then
        assertEquals(expectedResponse, result)
        assertEquals(testEvents, result.data)
        verify(eventService).getScheduledEvents(LocalDate.of(2024, 1, 1))
    }

    @Test
    fun `should load events for portfolio successfully`() {
        // Given
        val portfolioId = "test-portfolio-1"
        val fromDate = "2024-01-01"

        // When
        val result = eventMcpServer.loadEventsForPortfolio(portfolioId, fromDate)

        // Then
        assertEquals(portfolioId, result["portfolioId"])
        assertEquals(fromDate, result["fromDate"])
        assertEquals("loading_started", result["status"])
        assertTrue(result["message"].toString().contains(portfolioId))
        verify(eventLoader).loadEvents(portfolioId, fromDate)
    }

    @Test
    fun `should backfill events successfully`() {
        // Given
        val portfolioId = "test-portfolio-1"
        val fromDate = "2024-01-01"
        val toDate = "2024-01-31"

        // When
        val result = eventMcpServer.backfillEvents(portfolioId, fromDate, toDate)

        // Then
        assertEquals(portfolioId, result["portfolioId"])
        assertEquals(fromDate, result["fromDate"])
        assertEquals(toDate, result["toDate"])
        assertEquals("backfill_completed", result["status"])
        assertTrue(result["message"].toString().contains("backfill completed"))
        verify(backFillService).backFillEvents(portfolioId, fromDate, toDate)
    }

    @Test
    fun `should backfill events with default toDate when not provided`() {
        // Given
        val portfolioId = "test-portfolio-1"
        val fromDate = "2024-01-01"

        // When
        val result = eventMcpServer.backfillEvents(portfolioId, fromDate)

        // Then
        assertEquals(portfolioId, result["portfolioId"])
        assertEquals(fromDate, result["fromDate"])
        assertEquals(fromDate, result["toDate"]) // Should default to fromDate
        verify(backFillService).backFillEvents(portfolioId, fromDate, fromDate)
    }

    @Test
    fun `should get asset events for date successfully`() {
        // Given
        val assetIds = "asset-1,asset-2,asset-3"
        val recordDate = "2024-01-15"
        val expectedAssetIdList = listOf("asset-1", "asset-2", "asset-3")
        whenever(eventService.find(expectedAssetIdList, LocalDate.of(2024, 1, 15)))
            .thenReturn(testEvents)

        // When
        val result = eventMcpServer.getAssetEventsForDate(assetIds, recordDate)

        // Then
        assertEquals(testEvents, result.data)
        verify(eventService).find(expectedAssetIdList, LocalDate.of(2024, 1, 15))
    }

    @Test
    fun `should handle asset IDs with spaces correctly`() {
        // Given
        val assetIds = "asset-1, asset-2 , asset-3"
        val recordDate = "2024-01-15"
        val expectedAssetIdList = listOf("asset-1", "asset-2", "asset-3")
        whenever(eventService.find(expectedAssetIdList, LocalDate.of(2024, 1, 15)))
            .thenReturn(testEvents)

        // When
        val result = eventMcpServer.getAssetEventsForDate(assetIds, recordDate)

        // Then
        assertEquals(testEvents, result.data)
        verify(eventService).find(expectedAssetIdList, LocalDate.of(2024, 1, 15))
    }

    @Test
    fun `should return available tools correctly`() {
        // When
        val tools = eventMcpServer.getAvailableTools()

        // Then
        assertEquals(7, tools.size)
        assertTrue(tools.containsKey("get_event"))
        assertTrue(tools.containsKey("get_asset_events"))
        assertTrue(tools.containsKey("get_events_in_date_range"))
        assertTrue(tools.containsKey("get_scheduled_events"))
        assertTrue(tools.containsKey("load_events_for_portfolio"))
        assertTrue(tools.containsKey("backfill_events"))
        assertTrue(tools.containsKey("get_asset_events_for_date"))

        // Verify descriptions are meaningful
        assertTrue(tools["get_event"]!!.contains("corporate event by ID"))
        assertTrue(tools["load_events_for_portfolio"]!!.contains("external sources"))
    }

    @Test
    fun `should handle exceptions gracefully when getting event`() {
        // Given
        whenever(eventService["invalid-event"]).thenThrow(RuntimeException("Event not found"))

        // When & Then
        assertThrows(RuntimeException::class.java) {
            eventMcpServer.getEvent("invalid-event")
        }
    }

    @Test
    fun `should handle exceptions gracefully when loading events`() {
        // Given
        whenever(eventLoader.loadEvents(any(), any())).thenThrow(RuntimeException("Load failed"))

        // When & Then
        assertThrows(RuntimeException::class.java) {
            eventMcpServer.loadEventsForPortfolio("invalid-portfolio", "2024-01-01")
        }
    }
}