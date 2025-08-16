package com.beancounter.event.kafka

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.contracts.Payload.Companion.DATA
import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.input.ImportFormat
import com.beancounter.common.input.TrustedEventInput
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.event.Constants.Companion.ALPHA
import com.beancounter.event.Constants.Companion.KMI
import com.beancounter.event.Constants.Companion.NZD
import com.beancounter.event.Constants.Companion.USD
import com.beancounter.event.contract.CorporateEventResponse
import com.beancounter.event.service.BackFillService
import com.beancounter.event.service.EventService
import com.beancounter.event.service.PositionService
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Duration

private const val EMAIL = "blah@blah.com"

/**
 * Test inbound Kafka corporate action events
 *
 * IMPORTANT: This test uses its own isolated Spring context and stub runner configuration
 * rather than the shared context approach used by other tests. This is necessary because:
 *
 * 1. Kafka tests are more complex and require isolated environments
 * 2. Embedded Kafka broker needs its own configuration
 * 3. Stub runner timing issues occur with shared contexts
 * 4. Kafka consumer/producer setup is sensitive to context reuse
 *
 * Configuration:
 * - Uses "kafka" profile (application-kafka.yaml)
 * - Fixed stub runner ports: 11999 (svc-data), 12999 (svc-position)
 * - @DirtiesContext to ensure clean state between tests
 * - Separate from shared context ports (10990-10993)
 */

@EmbeddedKafka(
    partitions = 1,
    topics = [
        StubbedEvents.TRN_EVENT,
        StubbedEvents.CA_EVENT
    ],
    brokerProperties = ["log.dir=./build/kafka", "auto.create.topics.enable=true"]
)
// Kafka tests use fixed ports to avoid timing issues with shared contexts
// These ports are separate from the shared context ports (10990-10993)
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = [
        "org.beancounter:svc-data:0.1.1:stubs:11999",
        "org.beancounter:svc-position:0.1.1:stubs:12999"
    ]
)
@Tag("kafka")
@SpringBootTest
// Uses dedicated "kafka" profile instead of shared context profiles
// This ensures Kafka tests have their own isolated configuration
@ActiveProfiles("kafka")
@AutoConfigureMockMvc
@AutoConfigureMockAuth
// Use @DirtiesContext (not AFTER_CLASS) to ensure complete isolation for Kafka tests
// This prevents any shared state issues that could affect Kafka consumer/producer behavior
@DirtiesContext
class StubbedEvents {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoSpyBean
    private lateinit var eventService: EventService

    @MockitoSpyBean
    private lateinit var backfillService: BackFillService

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private lateinit var embeddedKafkaBroker: EmbeddedKafkaBroker

    @Autowired
    private lateinit var positionService: PositionService

    private val systemUser =
        SystemUser(
            id = EMAIL,
            email = EMAIL,
            true,
            since = DateUtils().getFormattedDate("2020-03-08")
        )

    @BeforeEach
    fun mockLogin() {
        mockAuthConfig.login()
    }

    var portfolio: Portfolio =
        Portfolio(
            id = "TEST",
            code = "TEST",
            name = "NZD Portfolio",
            currency = NZD,
            base = USD,
            owner = systemUser
        )
    val caDate = "2020-05-01"

    @Test
    fun is_NoQuantityOnDateNull() {
        val corporateEvent =
            CorporateEvent(
                id = null,
                trnType = TrnType.DIVI,
                recordDate = DateUtils().getFormattedDate(caDate),
                source = ALPHA,
                assetId = "MSFT",
                rate = BigDecimal("0.2625")
            )
        val trnEvent =
            positionService.process(
                portfolio,
                corporateEvent
            )
        assertThat(trnEvent.trnInput.trnType).isEqualTo(TrnType.IGNORE)
    }

    @Test
    fun is_DividendTransactionGenerated() {
        val consumerProps =
            KafkaTestUtils.consumerProps(
                "event-test",
                "false",
                embeddedKafkaBroker
            )
        consumerProps["session.timeout.ms"] = 6000
        consumerProps[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        val cf = DefaultKafkaConsumerFactory<String, String>(consumerProps)
        val consumer = cf.createConsumer()
        embeddedKafkaBroker.consumeFromEmbeddedTopics(
            consumer,
            TRN_EVENT
        )
        val corporateEvent =
            CorporateEvent(
                trnType = TrnType.DIVI,
                recordDate = DateUtils().getFormattedDate(caDate),
                source = ALPHA,
                assetId = KMI,
                rate = BigDecimal("0.2625")
            )
        val eventInput = TrustedEventInput(corporateEvent)
        val trnEvents = eventService.process(eventInput)
        assertThat(trnEvents).isNotNull.hasSize(1)

        // Check the receiver gets what we send
        val record = KafkaTestUtils.getSingleRecord(consumer, TRN_EVENT, Duration.ofSeconds(10))
        verify(
            portfolio,
            trnEvents,
            record
        )
        val events = eventService.forAsset(KMI)
        assertThat(events).hasSize(1)
        val (id) = events.iterator().next()
        val token = mockAuthConfig.getUserToken(systemUser)
        // Reprocess the corporate event
        val mvcResult =
            mockMvc
                .perform(
                    post("/$id")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                ).andExpect(
                    status().isAccepted
                ).andReturn()

        val eventsResponse =
            objectMapper.readValue(
                mvcResult.response.contentAsString,
                CorporateEventResponse::class.java
            )
        assertThat(eventsResponse).isNotNull.hasFieldOrProperty(DATA)
        val record2 = KafkaTestUtils.getSingleRecord(consumer, TRN_EVENT, Duration.ofSeconds(10))
        verify(
            portfolio,
            trnEvents,
            record2
        )
        consumer.close()

        mockMvc
            .perform(
                post("/backfill/${portfolio.id}/$caDate/$caDate")
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
            ).andExpect(
                status().isAccepted
            ).andReturn()

        Thread.sleep(400)

        Mockito
            .verify(
                backfillService,
                Mockito.times(1)
            ).backFillEvents(
                portfolio.id,
                caDate,
                caDate
            )

        // Verify that the backfill request is dispatched, but not for cash
        Mockito
            .verify(
                eventService,
                Mockito.times(1)
            ).process(any())
    }

    // We're working with exactly the same event, so output should be the same
    private fun verify(
        portfolio: Portfolio,
        trnEvents: Collection<TrustedTrnEvent>,
        consumerRecord: ConsumerRecord<String, String>
    ) {
        assertThat(consumerRecord.value()).isNotNull
        val received =
            objectMapper.readValue(
                consumerRecord.value(),
                TrustedTrnEvent::class.java
            )
        val (portfolio1, importFormat, message, trnInput) = trnEvents.iterator().next()
        assertThat(portfolio1)
            .usingRecursiveComparison()
            .isEqualTo(portfolio)
        assertTrue(importFormat == ImportFormat.BC)
        assertThat(message).isEmpty()
        assertThat(received)
            .isNotNull
            .hasFieldOrProperty("trnInput")
            .hasFieldOrPropertyWithValue(
                "portfolio.id",
                portfolio1.id
            )
        assertThat(trnInput)
            .isNotNull
            .hasFieldOrPropertyWithValue(
                "trnType",
                TrnType.DIVI
            ).hasFieldOrPropertyWithValue(
                "status",
                TrnStatus.PROPOSED
            ).hasFieldOrPropertyWithValue(
                "tradeAmount",
                BigDecimal("14.70")
            ).hasFieldOrPropertyWithValue(
                "tax",
                BigDecimal("6.30")
            ).hasFieldOrPropertyWithValue(
                "quantity",
                BigDecimal("80.0")
            )
    }

    companion object {
        const val TRN_EVENT = "testTrnEvent"
        const val CA_EVENT = "testCaEvent"
    }
}