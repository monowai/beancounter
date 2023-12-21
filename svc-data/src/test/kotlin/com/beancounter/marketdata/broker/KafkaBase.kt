package com.beancounter.marketdata.broker

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.marketdata.MarketDataBoot
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles

@EmbeddedKafka(
    partitions = 1,
    topics = [
        KafkaTrnExportImportTest.TOPIC_CSV_IO,
        KafkaMarketDataTest.TOPIC_EVENT,
    ],
    bootstrapServersProperty = "spring.kafka.bootstrap-servers",
    brokerProperties = [
        "log.dir=./build/kafka-trn",
    ],
)
@SpringBootTest(classes = [MarketDataBoot::class])
@ActiveProfiles("kafka")
@Tag("slow")
@AutoConfigureWireMock(port = 0)
@AutoConfigureMockAuth
@AutoConfigureMockMvc
@DirtiesContext
class KafkaBase {
    // Setup so that the wiring is tested
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    lateinit var embeddedKafkaBroker: EmbeddedKafkaBroker
}
