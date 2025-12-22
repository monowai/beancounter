package com.beancounter.event

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.TokenService
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.input.TrustedEventInput
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.event.integration.PositionGateway
import com.beancounter.event.service.EventService
import com.beancounter.event.service.PositionService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Simple flow of various corporate events for Microsoft.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockAuth
class TestMsftFlow {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var positionGateway: PositionGateway

    @Autowired
    private lateinit var eventService: EventService

    @Autowired
    private lateinit var positionService: PositionService

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Test
    fun is_DividendRequestIgnoreDueToZeroHolding() {
        assertThat(mockAuthConfig).isNotNull
        assertThat(positionGateway).isNotNull

        val trustedEvent =
            objectMapper.readValue(
                ClassPathResource("/msft-flow/1-event.json").file,
                TrustedEventInput::class.java
            )
        val whereHeld =
            objectMapper.readValue(
                ClassPathResource("/msft-flow/2-where-held.json").file,
                PortfoliosResponse::class.java
            )
        val positionResponse =
            objectMapper.readValue(
                ClassPathResource("/msft-flow/3-position.json").file,
                PositionResponse::class.java
            )
        val mockPositionGateway = Mockito.mock(PositionGateway::class.java)
        val (_, _, _, assetId, recordDate) = trustedEvent.data

        `when`(
            mockPositionGateway["demo", assetId, recordDate.toString()]
        ).thenReturn(positionResponse)

        val portfolio = whereHeld.data.iterator().next()
        `when`(
            mockPositionGateway.query(
                "demo",
                TrustedTrnQuery(
                    portfolio,
                    recordDate,
                    assetId
                )
            )
        ).thenReturn(positionResponse)

        val mockPortfolioServiceClient = Mockito.mock(PortfolioServiceClient::class.java)
        `when`(mockPortfolioServiceClient.getWhereHeld(assetId, recordDate))
            .thenReturn(whereHeld)

        val tokenService = Mockito.mock(TokenService::class.java)
        `when`(tokenService.bearerToken)
            .thenReturn("demo")

        positionService.setTokenService(tokenService)
        positionService.setPortfolioClientService(mockPortfolioServiceClient)
        val results = eventService.process(trustedEvent)
        assertThat(results).isEmpty()
    }
}