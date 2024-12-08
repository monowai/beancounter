package com.beancounter.marketdata.portfolio

import com.beancounter.common.model.Portfolio
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(
    value = ["kafka.enabled"],
    matchIfMissing = true,
)
class PortfolioKafkaListener(
    private val portfolioService: PortfolioService,
) {
    @KafkaListener(
        topics = ["#{@posMvTopic}"],
        errorHandler = "bcErrorHandler",
    )
    fun listen(portfolioJson: String) {
        val portfolio =
            objectMapper.readValue(
                portfolioJson,
                Portfolio::class.java,
            )
        portfolioService.maintain(portfolio)
    }
}
