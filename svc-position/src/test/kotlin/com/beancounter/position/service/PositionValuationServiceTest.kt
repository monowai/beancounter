package com.beancounter.position.service

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.client.services.PriceService
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Portfolio
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.Constants.Companion.US
import com.beancounter.position.irr.IrrCalculator
import com.beancounter.position.utils.FxUtils
import com.beancounter.position.utils.TestHelpers
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
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Test suite for PositionValuationService to ensure proper position valuation functionality.
 *
 * This class tests:
 * - Position value computation when assets are provided
 * - Handling of empty asset lists
 * - Integration with price and FX services
 * - Market data processing and valuation calculations
 *
 * Tests verify that the PositionValuationService correctly computes
 * financial position values based on market data and exchange rates.
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

    @Mock
    private lateinit var calculationSupport: PositionCalculationSupport

    private lateinit var valuationService: PositionValuationService

    val portfolio: Portfolio = TestHelpers.createTestPortfolio("PositionValuationServiceTest")

    @BeforeEach
    fun setup() {
        val config =
            PositionValuationConfig(
                MarketValue(
                    Gains(),
                    DateUtils()
                ),
                fxUtils,
                priceService,
                fxRateService,
                tokenService,
                DateUtils(),
                irrCalculator
            )
        valuationService = PositionValuationService(config, calculationSupport)
    }

    @Test
    fun `should correctly compute values when assets are not empty`() {
        // Given
        whenever(tokenService.bearerToken).thenReturn("Token Value")
        val asset = TestHelpers.createTestAsset("Asset1", US.code)
        val assetInputs = setOf(TestHelpers.createTestAssetInput(US.code, asset.code))
        val positions =
            TestHelpers.createTestPositions(
                portfolio,
                listOf(TestHelpers.createTestPosition(asset, portfolio))
            )

        whenever(fxUtils.buildRequest(any(), any())).thenReturn(FxRequest())
        whenever(priceService.getPrices(any(), any())).thenReturn(
            PriceResponse(listOf(TestHelpers.createTestMarketData(asset)))
        )
        whenever(fxRateService.getRates(any(), any())).thenReturn(FxResponse())

        // Mock calculation support methods to return proper MoneyValues
        val mockMoneyValues = MoneyValues(portfolio.currency)
        whenever(calculationSupport.calculateTradeMoneyValues(any(), any())).thenReturn(mockMoneyValues)
        whenever(calculationSupport.calculateBaseMoneyValues(any(), any(), any())).thenReturn(mockMoneyValues)
        whenever(
            calculationSupport.calculatePortfolioMoneyValues(any(), any(), any(), any())
        ).thenReturn(mockMoneyValues)
        whenever(calculationSupport.calculateRoi(any())).thenReturn(BigDecimal.ZERO)
        whenever(irrCalculator.calculate(any())).thenReturn(0.0)

        // When
        val result = valuationService.value(positions, assetInputs)

        // Then
        assertThat(result).isNotNull()
        verify(priceService).getPrices(any(), any())
    }

    @Test
    fun `should return positions unchanged when assets are empty`() {
        // Given
        val positions = TestHelpers.createTestPositions(portfolio, emptyList())

        // When
        val result = valuationService.value(positions, emptyList())

        // Then
        assertThat(result).isEqualTo(positions)
    }

    @Test
    fun `should use ROI for positions held less than minHoldingDays`() {
        // Given - position opened 100 days ago (less than 365 day threshold)
        whenever(tokenService.bearerToken).thenReturn("Token Value")
        val asset = TestHelpers.createTestAsset("SHORT_HOLD", US.code)
        val assetInputs = setOf(TestHelpers.createTestAssetInput(US.code, asset.code))
        val position = TestHelpers.createTestPosition(asset, portfolio)
        position.dateValues.opened = LocalDate.now().minusDays(100)
        val positions = TestHelpers.createTestPositions(portfolio, listOf(position))

        whenever(fxUtils.buildRequest(any(), any())).thenReturn(FxRequest())
        whenever(priceService.getPrices(any(), any())).thenReturn(
            PriceResponse(listOf(TestHelpers.createTestMarketData(asset)))
        )
        whenever(fxRateService.getRates(any(), any())).thenReturn(FxResponse())

        val expectedRoi = BigDecimal("0.38")
        val mockMoneyValues = MoneyValues(portfolio.currency)
        whenever(calculationSupport.calculateTradeMoneyValues(any(), any())).thenReturn(mockMoneyValues)
        whenever(calculationSupport.calculateBaseMoneyValues(any(), any(), any())).thenReturn(mockMoneyValues)
        whenever(calculationSupport.calculatePortfolioMoneyValues(any(), any(), any(), any()))
            .thenReturn(mockMoneyValues)
        whenever(calculationSupport.calculateRoi(any())).thenReturn(expectedRoi)
        // IRR calculator returns different value - but for short holding period, ROI should be used
        whenever(irrCalculator.calculate(any())).thenReturn(0.27)

        // When
        valuationService.value(positions, assetInputs)

        // Then - verify updateTotals was called with ROI value for both roi AND irr parameters
        // For short holding periods, the IRR should equal the ROI (called 3 times for trade/base/portfolio)
        verify(calculationSupport, org.mockito.kotlin.times(3))
            .updateTotals(any(), any(), org.mockito.kotlin.eq(expectedRoi), org.mockito.kotlin.eq(expectedRoi))
    }

    @Test
    fun `should use IRR calculator for positions held longer than minHoldingDays`() {
        // Given - position opened 400 days ago (more than 365 day threshold)
        whenever(tokenService.bearerToken).thenReturn("Token Value")
        val asset = TestHelpers.createTestAsset("LONG_HOLD", US.code)
        val assetInputs = setOf(TestHelpers.createTestAssetInput(US.code, asset.code))
        val position = TestHelpers.createTestPosition(asset, portfolio)
        position.dateValues.opened = LocalDate.now().minusDays(400)
        val positions = TestHelpers.createTestPositions(portfolio, listOf(position))

        whenever(fxUtils.buildRequest(any(), any())).thenReturn(FxRequest())
        whenever(priceService.getPrices(any(), any())).thenReturn(
            PriceResponse(listOf(TestHelpers.createTestMarketData(asset)))
        )
        whenever(fxRateService.getRates(any(), any())).thenReturn(FxResponse())

        val expectedRoi = BigDecimal("0.38")
        val mockMoneyValues = MoneyValues(portfolio.currency)
        whenever(calculationSupport.calculateTradeMoneyValues(any(), any())).thenReturn(mockMoneyValues)
        whenever(calculationSupport.calculateBaseMoneyValues(any(), any(), any())).thenReturn(mockMoneyValues)
        whenever(calculationSupport.calculatePortfolioMoneyValues(any(), any(), any(), any()))
            .thenReturn(mockMoneyValues)
        whenever(calculationSupport.calculateRoi(any())).thenReturn(expectedRoi)
        whenever(irrCalculator.calculate(any())).thenReturn(0.27)

        // When
        valuationService.value(positions, assetInputs)

        // Then - verify updateTotals was called with different ROI and IRR values
        // For long holding periods, the IRR should be calculated (different from ROI)
        // Called 3 times for trade/base/portfolio
        verify(calculationSupport, org.mockito.kotlin.times(3))
            .updateTotals(any(), any(), org.mockito.kotlin.eq(expectedRoi), org.mockito.kotlin.eq(BigDecimal(0.27)))
    }
}