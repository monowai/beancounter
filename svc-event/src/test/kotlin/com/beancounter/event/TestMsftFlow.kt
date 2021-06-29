package com.beancounter.event

import com.beancounter.auth.common.TokenService
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.client.services.PortfolioServiceClient.PortfolioGw
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.input.TrustedEventInput
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.event.integration.PositionGateway
import com.beancounter.event.service.EventService
import com.beancounter.event.service.PositionService
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.core.io.ClassPathResource
import org.springframework.test.context.ActiveProfiles

/**
 * Simple flow of various corporate events for Microsoft.
 */
@SpringBootTest(classes = [EventBoot::class])
@ActiveProfiles("test") // Ignore Kafka
@MockBean(PositionGateway::class)
class TestMsftFlow {
    @Autowired
    private lateinit var eventService: EventService

    @Autowired
    private lateinit var positionService: PositionService

    @Autowired
    private lateinit var positionGateway: PositionGateway
    private val objectMapper: ObjectMapper = BcJson().objectMapper

    @Test
    @Throws(Exception::class)
    fun is_DividendRequestIgnoreDueToZeroHolding() {
        val trustedEvent = objectMapper.readValue(
            ClassPathResource("/msft-flow/1-event.json").file,
            TrustedEventInput::class.java
        )
        val whereHeld = objectMapper.readValue(
            ClassPathResource("/msft-flow/2-where-held.json").file, PortfoliosResponse::class.java
        )
        val positionResponse = objectMapper.readValue(
            ClassPathResource("/msft-flow/3-position.json").file, PositionResponse::class.java
        )
        val positionGateway = Mockito.mock(PositionGateway::class.java)
        val (_, _, _, assetId, recordDate) = trustedEvent.data
        val dateUtils = DateUtils()

        Mockito.`when`(
            positionGateway["demo", assetId, dateUtils.getDateString(recordDate)]
        ).thenReturn(positionResponse)

        val portfolio = whereHeld.data.iterator().next()
        Mockito.`when`(
            positionGateway
                .query(
                    "demo",
                    TrustedTrnQuery(portfolio, recordDate, assetId)
                )
        ).thenReturn(positionResponse)

        val portfolioGw = Mockito.mock(PortfolioGw::class.java)
        Mockito.`when`(
            portfolioGw.getWhereHeld(
                "demo",
                assetId,
                dateUtils.getDateString(recordDate)
            )
        )
            .thenReturn(whereHeld)
        val tokenService = Mockito.mock(TokenService::class.java)
        Mockito.`when`(tokenService.bearerToken)
            .thenReturn("demo")
        val portfolioServiceClient = PortfolioServiceClient(portfolioGw, tokenService)
        positionService.setTokenService(tokenService)
        positionService.setPortfolioClientService(portfolioServiceClient)
        val results = eventService.processMessage(trustedEvent)
        assertThat(results).isEmpty()
    }
}
