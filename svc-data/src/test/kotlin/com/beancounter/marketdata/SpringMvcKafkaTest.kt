package com.beancounter.marketdata

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.common.utils.KafkaUtils
import com.beancounter.marketdata.broker.KafkaMarketStackDataTest.Companion.TOPIC_EVENT
import com.beancounter.marketdata.broker.KafkaMarketStackDataTest.Companion.TOPIC_MV
import com.beancounter.marketdata.broker.KafkaTrnExportImportTest.Companion.TOPIC_CSV_IO
import jakarta.transaction.Transactional
import org.junit.jupiter.api.Tag
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.context.annotation.Import
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS

/**
 * A custom annotation that combines common configurations for Spring Boot integration tests.
 *
 */
@Suppress("unused")
@Target(CLASS)
@Retention(RUNTIME)
@EmbeddedKafka(
    partitions = 1,
    topics = [
        TOPIC_CSV_IO,
        TOPIC_EVENT,
        TOPIC_MV
    ],
    brokerProperties = [
        "log.dirs=./build/kafka-trn"
    ]
)
@SpringBootTest
@Tag("kafka")
// Slow Test
@AutoConfigureWireMock(port = 0)
@AutoConfigureMockAuth
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles
@DirtiesContext
@Import(KafkaUtils::class)
annotation class SpringMvcKafkaTest(
    /**
     * Defines the active profiles to be used for the annotated test class.
     */
    val profiles: Array<String> = ["kafka"]
)