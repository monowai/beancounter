package com.beancounter.event.kafka

import com.beancounter.client.AssetService
import com.beancounter.common.contracts.Payload.Companion.DATA
import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.input.ImportFormat
import com.beancounter.common.input.TrustedEventInput
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.event.Constants.Companion.NZD
import com.beancounter.event.Constants.Companion.USD
import com.beancounter.event.Constants.Companion.alpha
import com.beancounter.event.Constants.Companion.kmi
import com.beancounter.event.EventBoot
import com.beancounter.event.contract.CorporateEventResponse
import com.beancounter.event.service.EventService
import com.beancounter.event.service.PositionService
import com.fasterxml.jackson.core.JsonProcessingException
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal

/**
 * Test inbound Kafka corporate action events
 */

@EmbeddedKafka(
    partitions = 1,
    topics = [
        StubbedEvents.TRN_EVENT,
        StubbedEvents.CA_EVENT
    ],
    bootstrapServersProperty = "spring.kafka.bootstrap-servers",
    brokerProperties = ["log.dir=./build/kafka", "auto.create.topics.enable=true"]
)
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = [
        "org.beancounter:svc-data:+:stubs:11999",
        "org.beancounter:svc-position:+:stubs:12999"
    ]
)
@Tag("slow")
@SpringBootTest(classes = [EventBoot::class], properties = ["auth.enabled=false"])
@ActiveProfiles("kafka")
class StubbedEvents {
    private val om = BcJson().objectMapper

    @Autowired
    private lateinit var eventService: EventService

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private lateinit var embeddedKafkaBroker: EmbeddedKafkaBroker

    @Autowired
    private lateinit var positionService: PositionService

    @Autowired
    private lateinit var wac: WebApplicationContext
    private val owner = SystemUser(
        id = "blah@blah.com",
        email = "blah@blah.com",
        true,
        DateUtils().getDate("2020-03-08")
    )

    var portfolio: Portfolio = Portfolio(
        id = "TEST",
        code = "TEST",
        name = "NZD Portfolio",
        currency = NZD,
        base = USD,
        owner = owner
    )
    val caDate = "2020-05-01"

    @Test
    fun is_NoQuantityOnDateNull() {
        val corporateEvent = CorporateEvent(
            TrnType.DIVI,
            DateUtils().getDate(caDate),
            alpha,
            "MSFT",
            BigDecimal("0.2625")
        )
        val trnEvent = positionService.process(portfolio, corporateEvent)
        assertThat(trnEvent).isNull()
    }

    @Test
    @Throws(Exception::class)
    fun is_DividendTransactionGenerated() {
        val consumerProps = KafkaTestUtils.consumerProps("event-test", "false", embeddedKafkaBroker)
        consumerProps["session.timeout.ms"] = 6000
        consumerProps[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        val cf = DefaultKafkaConsumerFactory<String, String>(consumerProps)
        val consumer = cf.createConsumer()
        embeddedKafkaBroker.consumeFromEmbeddedTopics(consumer, TRN_EVENT)
        val corporateEvent = CorporateEvent(
            TrnType.DIVI,
            DateUtils().getDate(caDate),
            alpha,
            kmi,
            BigDecimal("0.2625")
        )
        val eventInput = TrustedEventInput(corporateEvent)
        val trnEvents = eventService.processMessage(eventInput)
        assertThat(trnEvents).isNotNull.hasSize(1)

        // Check the receiver gets what we send
        verify(portfolio, trnEvents, KafkaTestUtils.getSingleRecord(consumer, TRN_EVENT))
        val events = eventService.forAsset(kmi)
        assertThat(events).hasSize(1)
        val (id) = events.iterator().next()
        val mockMvc = MockMvcBuilders.webAppContextSetup(wac).build()

        // Reprocess the corporate event
        val mvcResult = mockMvc.perform(
            post("/{id}", id)
        ).andExpect(
            status().isAccepted
        ).andReturn()

        val eventsResponse = om.readValue(
            mvcResult.response.contentAsString,
            CorporateEventResponse::class.java
        )
        assertThat(eventsResponse).isNotNull.hasFieldOrProperty(DATA)
        verify(portfolio, trnEvents, KafkaTestUtils.getSingleRecord(consumer, TRN_EVENT))

        // Back-fill Portfolio events up to, and as at, the supplied date
        val assetSpy = Mockito.spy(AssetService::class.java)
        positionService.setAssetService(assetSpy)

        mockMvc.perform(
            post("/backfill/{portfolioId}/{caDate}", portfolio.id, caDate)
        ).andExpect(
            status().isAccepted
        ).andReturn()

        Thread.sleep(400)

        // Verify that the backfill request is dispatched, but not for cash
        Mockito.verify(
            assetSpy,
            Mockito.times(2)
        )
            .backFillEvents(any())
    }

    // We're working with exactly the same event, so output should be the same
    @Throws(JsonProcessingException::class)
    private fun verify(
        portfolio: Portfolio,
        trnEvents: Collection<TrustedTrnEvent>,
        consumerRecord: ConsumerRecord<String, String>
    ) {
        assertThat(consumerRecord.value()).isNotNull
        val received = om.readValue(consumerRecord.value(), TrustedTrnEvent::class.java)
        val (portfolio1, importFormat, message, trnInput) = trnEvents.iterator().next()
        assertThat(portfolio1)
            .usingRecursiveComparison().isEqualTo(portfolio)
        assertTrue(importFormat == ImportFormat.BC)
        assertThat(message).isEmpty()
        assertThat(received)
            .isNotNull
            .hasFieldOrProperty("trnInput")
            .hasFieldOrPropertyWithValue("portfolio.id", portfolio1.id)
        assertThat(trnInput)
            .isNotNull
            .hasFieldOrPropertyWithValue("trnType", TrnType.DIVI)
            .hasFieldOrPropertyWithValue("status", TrnStatus.PROPOSED)
            .hasFieldOrPropertyWithValue("tradeAmount", BigDecimal("14.70"))
            .hasFieldOrPropertyWithValue("tax", BigDecimal("6.30"))
            .hasFieldOrPropertyWithValue("quantity", BigDecimal("80.0"))
    }

    companion object {
        const val TRN_EVENT = "testTrnEvent"
        const val CA_EVENT = "testCaEvent"
    }
}
