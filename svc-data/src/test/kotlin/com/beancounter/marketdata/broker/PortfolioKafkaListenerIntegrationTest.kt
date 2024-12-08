package com.beancounter.marketdata.broker

import com.beancounter.common.model.Portfolio
import com.beancounter.marketdata.SpringMvcKafkaTest
import com.beancounter.marketdata.config.KafkaConfig
import com.beancounter.marketdata.event.EventProducer
import com.beancounter.marketdata.portfolio.PortfolioService
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.kafka.core.KafkaTemplate
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

// @EmbeddedKafka(partitions = 1, topics = ["\${beancounter.topics.pos.mv}"])
@SpringMvcKafkaTest
class PortfolioKafkaListenerIntegrationTest {
    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    @MockBean
    private lateinit var portfolioService: PortfolioService

    @Autowired
    private lateinit var kafkaConfig: KafkaConfig

    @MockBean
    private lateinit var eventProducer: EventProducer

    @Test
    fun testKafkaListener() {
        val portfolio =
            Portfolio(
                id = "1",
                code = "TEST",
                name = "Test Portfolio",
                marketValue = BigDecimal.TEN,
                irr = BigDecimal.ONE,
            )
        kafkaTemplate.send(
            kafkaConfig.topicPosMvName,
            portfolio,
        )

        // Wait for the listener to process the message
        await()
            .atMost(
                10,
                TimeUnit.SECONDS,
            ).untilAsserted {
                verify(portfolioService).maintain(portfolio)
            }
    }
}
