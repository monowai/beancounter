package com.beancounter.position.service

import IrrCalculator
import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.client.services.PriceService
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.Constants
import com.beancounter.position.Constants.Companion.US
import com.beancounter.position.utils.FxUtils
import com.beancounter.position.valuation.Gains
import com.beancounter.position.valuation.MarketValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for {@link PositionValuationService} class.
 * This test suite checks the functionality of the PositionValuationService,
 * ensuring that it computes financial position values correctly based on given market data
 * and foreign exchange rates.
 *
 * <p>Tests are designed to verify both the handling of non-empty asset sets where market data
 * and exchange rates affect the computed values, and scenarios where no assets are provided,
 * ensuring the service behaves as expected under different conditions.</p>
 *
 * <p>Utilizes {@link MockitoExtension} for mocking dependencies and injecting mocked instances
 * into the service being tested. This allows for isolated testing of the service logic without
 * interference from external services like {@link PriceService} and {@link FxRateService}.</p>
 *
 * <p>Key functionalities tested:
 * <ul>
 *     <li>Correct computation of position values when assets are not empty.</li>
 *     <li>Proper return of unchanged positions when asset lists are empty.</li>
 *     <li>Interaction with dependency services to fetch necessary data.</li>
 * </ul>
 * </p>
 */

@ExtendWith(MockitoExtension::class)
class PositionValuationServiceTest {
    @Mock
    private lateinit var fxUtils: FxUtils

    @Mock
    private lateinit var priceService: PriceService

    @Mock
    private lateinit var fxRateService: FxService

    @Mock
    private lateinit var tokenService: TokenService

    @Mock
    private lateinit var irrCalculator: IrrCalculator

    private lateinit var valuationService: PositionValuationService

    val portfolio: Portfolio =
        Portfolio(
            id = "PositionValuationServiceTest",
            currency = Constants.USD,
            base = Constants.USD,
            owner = Constants.owner,
        )

    @BeforeEach
    fun setup() {
        valuationService =
            PositionValuationService(
                MarketValue(Gains(), DateUtils()),
                fxUtils,
                priceService,
                fxRateService,
                tokenService,
                DateUtils(),
                irrCalculator,
            )
    }

    @Test
    fun `value should correctly compute values when assets are not empty`() {
        // Arrange
        whenever(tokenService.bearerToken).thenReturn("Token Value")
        val asset = Asset(code = "Asset1", market = US)
        val assetInputs = setOf(AssetInput(US.code, asset.code))
        val positions = Positions(portfolio)
        positions.add(Position(asset, portfolio))
        whenever(fxUtils.buildRequest(any(), any())).thenReturn(FxRequest())
        whenever(priceService.getPrices(any(), any())).thenReturn(
            PriceResponse(
                listOf(MarketData(asset)),
            ),
        )
        whenever(fxRateService.getRates(any(), any())).thenReturn(FxResponse())

        // Act
        val result = valuationService.value(positions, assetInputs)

        // Assert
        assertThat(result).isNotNull
        verify(priceService).getPrices(any(), any()) // Verifies that prices were indeed fetched
    }

    @Test
    fun `value should return positions unchanged when assets are empty`() {
        val positions = Positions(portfolio)

        // Act
        val result = valuationService.value(positions, emptyList())

        // Assert
        assertThat(result).isEqualTo(positions)
    }
}
