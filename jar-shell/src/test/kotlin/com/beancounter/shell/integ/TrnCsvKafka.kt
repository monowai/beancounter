package com.beancounter.shell.integ

import com.beancounter.auth.AuthConfig
import com.beancounter.auth.AutoConfigureNoAuth
import com.beancounter.client.config.ClientConfig
import com.beancounter.client.ingest.TrnAdapter
import com.beancounter.client.sharesight.ShareSightConfig
import com.beancounter.client.sharesight.ShareSightFactory
import com.beancounter.common.input.ImportFormat
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.shell.Constants.Companion.MOCK
import com.beancounter.shell.integ.TrnCsvKafka.Companion.TOPIC
import com.beancounter.shell.kafka.KafkaTrnProducer
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension

/**
 * Kafka import integration tests.
 * Topic name needs to apply to the value in application-kafka.yaml
 */
@EmbeddedKafka(
    partitions = 1,
    topics = [TOPIC],
    bootstrapServersProperty = "spring.kafka.bootstrap-servers",
    brokerProperties = ["log.dir=./build/kafka", "listeners=PLAINTEXT://localhost:\${kafka.broker.port}", "auto.create.topics.enable=true"],
)
@ExtendWith(
    SpringExtension::class,
)
@SpringBootTest(
    classes = [KafkaTrnProducer::class, ShareSightConfig::class, ClientConfig::class, KafkaAutoConfiguration::class],
)
@ActiveProfiles("kafka")
@AutoConfigureNoAuth
class TrnCsvKafka {
    private val log = LoggerFactory.getLogger(TrnCsvKafka::class.java)
    private final val abc = "ABC"
    private val row: List<String> = listOf(abc, abc, abc)

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private lateinit var embeddedKafkaBroker: EmbeddedKafkaBroker

    @MockBean
    private lateinit var authConfig: AuthConfig

    @Autowired
    private val kafkaTrnProducer: KafkaTrnProducer? = null

    @MockBean
    private lateinit var shareSightFactory: ShareSightFactory
    private lateinit var consumer: Consumer<String, String>
    private val objectMapper = BcJson().objectMapper

    @BeforeEach
    fun mockBeans() {
        log.debug("!!! {}", embeddedKafkaBroker.brokersAsString)
        val trnAdapter =
            Mockito.mock(
                TrnAdapter::class.java,
            )
        Mockito.`when`(trnAdapter.resolveAsset(row))
            .thenReturn(getTestAsset(MOCK, abc))

        Mockito.`when`(shareSightFactory.adapter(row)).thenReturn(trnAdapter)

        val consumerProps: MutableMap<String, Any> =
            KafkaTestUtils.consumerProps(
                "shell-test",
                "false",
                embeddedKafkaBroker,
            )
        consumerProps["session.timeout.ms"] = 6000
        consumerProps[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        val cf = DefaultKafkaConsumerFactory<String, String>(consumerProps)
        consumer = cf.createConsumer()
        embeddedKafkaBroker.consumeFromEmbeddedTopics(consumer, TOPIC)
    }

    @Test
    fun is_TrnRequestSendingCorrectly() {
        val trnRequest =
            TrustedTrnImportRequest(
                getPortfolio(),
                importFormat = ImportFormat.SHARESIGHT,
                row = row,
            )
        kafkaTrnProducer!!.write(trnRequest)
        log.info("Waiting for Result")
        try {
            val received = KafkaTestUtils.getSingleRecord(consumer, TOPIC)
            Assertions.assertThat(received.value()).isNotNull
            Assertions.assertThat(
                objectMapper.readValue(
                    received.value(),
                    TrustedTrnImportRequest::class.java,
                ),
            )
                .usingRecursiveComparison().isEqualTo(trnRequest)
        } finally {
            consumer.close()
            embeddedKafkaBroker.destroy()
        }
    }

    companion object {
        const val TOPIC: String = "shellTrnTopic"
    }
}
