package com.beancounter.position.service

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.common.contracts.AllocationData
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.Currency
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Position
import com.beancounter.common.model.Totals
import com.beancounter.position.composite.AssetConfigClient
import com.beancounter.position.composite.PrivateAssetConfigDto
import com.beancounter.position.composite.SubAccountDto
import com.beancounter.position.utils.TestHelpers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

/**
 * Unit tests for [AllocationService.convertPositionsToCurrency].
 */
@ExtendWith(MockitoExtension::class)
class AllocationServiceTest {
    @Mock
    private lateinit var tokenService: TokenService

    @Mock
    private lateinit var fxService: FxService

    @Mock
    private lateinit var assetConfigClient: AssetConfigClient

    private lateinit var allocationService: AllocationService

    @BeforeEach
    fun setUp() {
        allocationService = AllocationService(tokenService, fxService, assetConfigClient)
    }

    @Test
    fun `convertPositionsToCurrency applies FX rate to PORTFOLIO view`() {
        val usd = Currency("USD")
        val sgd = Currency("SGD")
        val portfolio = TestHelpers.createTestPortfolio(currencyCode = "USD")
        val position = TestHelpers.createTestPosition(portfolio = portfolio)
        val portfolioValues = MoneyValues(usd).apply { marketValue = BigDecimal("1000") }
        val baseValues = MoneyValues(usd).apply { marketValue = BigDecimal("1000") }
        val tradeValues = MoneyValues(usd).apply { marketValue = BigDecimal("1000") }
        position.moneyValues[Position.In.PORTFOLIO] = portfolioValues
        position.moneyValues[Position.In.BASE] = baseValues
        position.moneyValues[Position.In.TRADE] = tradeValues

        val positions = TestHelpers.createTestPositions(portfolio, listOf(position))
        positions.totals[Position.In.PORTFOLIO] =
            Totals(currency = usd, marketValue = BigDecimal("1000"))

        val pair = IsoCurrencyPair(from = "USD", to = "SGD")
        val rate = BigDecimal("1.35")
        whenever(tokenService.bearerToken).thenReturn("bearer")
        whenever(fxService.getRates(any<FxRequest>(), eq("bearer"))).thenReturn(
            FxResponse(
                FxPairResults(rates = mapOf(pair to FxRate(from = usd, to = sgd, rate = rate)))
            )
        )

        val result = allocationService.convertPositionsToCurrency(positions, "SGD", "2024-01-15")

        val converted =
            result.positions.values
                .first()
                .moneyValues[Position.In.PORTFOLIO]!!
        assertThat(converted.currency.code).isEqualTo("SGD")
        assertThat(converted.marketValue).isEqualByComparingTo("1350")
        // TRADE and BASE views should not be touched
        assertThat(
            result.positions.values
                .first()
                .moneyValues[Position.In.BASE]!!
                .currency.code
        ).isEqualTo("USD")
        assertThat(
            result.positions.values
                .first()
                .moneyValues[Position.In.TRADE]!!
                .currency.code
        ).isEqualTo("USD")
        assertThat(result.totals[Position.In.PORTFOLIO]!!.currency.code).isEqualTo("SGD")
        assertThat(result.totals[Position.In.PORTFOLIO]!!.marketValue).isEqualByComparingTo("1350")
    }

    @Test
    fun `convertPositionsToCurrency is a no-op when source equals target`() {
        val usd = Currency("USD")
        val portfolio = TestHelpers.createTestPortfolio(currencyCode = "USD")
        val position = TestHelpers.createTestPosition(portfolio = portfolio)
        position.moneyValues[Position.In.PORTFOLIO] =
            MoneyValues(usd).apply { marketValue = BigDecimal("1000") }
        val positions = TestHelpers.createTestPositions(portfolio, listOf(position))
        positions.totals[Position.In.PORTFOLIO] =
            Totals(currency = usd, marketValue = BigDecimal("1000"))

        val result = allocationService.convertPositionsToCurrency(positions, "USD", "2024-01-15")

        assertThat(
            result.positions.values
                .first()
                .moneyValues[Position.In.PORTFOLIO]!!
                .marketValue
        ).isEqualByComparingTo("1000")
        verify(fxService, org.mockito.kotlin.never()).getRates(any<FxRequest>(), any())
    }

    @Test
    fun `convertPositionsToCurrency returns positions unchanged when no FX rate resolves`() {
        val usd = Currency("USD")
        val portfolio = TestHelpers.createTestPortfolio(currencyCode = "USD")
        val position = TestHelpers.createTestPosition(portfolio = portfolio)
        position.moneyValues[Position.In.PORTFOLIO] =
            MoneyValues(usd).apply { marketValue = BigDecimal("1000") }
        val positions = TestHelpers.createTestPositions(portfolio, listOf(position))
        positions.totals[Position.In.PORTFOLIO] =
            Totals(currency = usd, marketValue = BigDecimal("1000"))

        whenever(tokenService.bearerToken).thenReturn("bearer")
        whenever(fxService.getRates(any<FxRequest>(), eq("bearer")))
            .thenReturn(FxResponse(FxPairResults(rates = emptyMap())))

        val result = allocationService.convertPositionsToCurrency(positions, "SGD", "2024-01-15")

        assertThat(
            result.positions.values
                .first()
                .moneyValues[Position.In.PORTFOLIO]!!
                .currency.code
        ).isEqualTo("USD")
        assertThat(result.totals[Position.In.PORTFOLIO]!!.marketValue).isEqualByComparingTo("1000")
    }

    @Test
    fun `calculateAllocation splits composite position into liquid and non-liquid via subaccount flags`() {
        val sgd = Currency("SGD")
        val portfolio = TestHelpers.createTestPortfolio(currencyCode = "SGD", baseCurrency = "SGD")

        val cpfAsset =
            TestHelpers.createTestAsset(code = "userA.CPF", marketCode = "PRIVATE").apply {
                assetCategory = AssetCategory(id = "POLICY", name = "Retirement Fund")
            }
        val cpf = Position(cpfAsset, portfolio)
        cpf.subAccounts["OA"] = BigDecimal("145000")
        cpf.subAccounts["SA"] = BigDecimal("78000")
        cpf.subAccounts["MA"] = BigDecimal("58000")
        cpf.moneyValues[Position.In.PORTFOLIO] =
            MoneyValues(sgd).apply { marketValue = BigDecimal("281000") }

        val positions = TestHelpers.createTestPositions(portfolio, listOf(cpf))
        positions.totals[Position.In.PORTFOLIO] =
            Totals(currency = sgd, marketValue = BigDecimal("281000"))

        whenever(assetConfigClient.find(cpfAsset.id)).thenReturn(
            PrivateAssetConfigDto(
                assetId = cpfAsset.id,
                policyType = "CPF",
                currency = "SGD",
                subAccounts =
                    listOf(
                        SubAccountDto(code = "OA", balance = BigDecimal("145000"), liquid = true),
                        SubAccountDto(code = "SA", balance = BigDecimal("78000"), liquid = true),
                        SubAccountDto(code = "MA", balance = BigDecimal("58000"), liquid = false)
                    )
            )
        )

        val data = allocationService.calculateAllocation(positions)

        // 145k OA + 78k SA = 223k liquid; 58k MA non-liquid.
        assertThat(data.compositeLiquid).isEqualByComparingTo("223000")
        assertThat(data.compositeNonLiquid).isEqualByComparingTo("58000")
        // Total unchanged — split is a refinement, not a re-count.
        assertThat(data.totalValue).isEqualByComparingTo("281000")
    }

    @Test
    fun `calculateAllocation skips composite lookup when position has no subAccounts`() {
        val sgd = Currency("SGD")
        val portfolio = TestHelpers.createTestPortfolio(currencyCode = "SGD", baseCurrency = "SGD")
        val asset = TestHelpers.createTestAsset(code = "VOO", marketCode = "US")
        val position = Position(asset, portfolio)
        position.moneyValues[Position.In.PORTFOLIO] =
            MoneyValues(sgd).apply { marketValue = BigDecimal("10000") }

        val positions = TestHelpers.createTestPositions(portfolio, listOf(position))
        positions.totals[Position.In.PORTFOLIO] =
            Totals(currency = sgd, marketValue = BigDecimal("10000"))

        val data = allocationService.calculateAllocation(positions)

        assertThat(data.compositeLiquid).isEqualByComparingTo("0")
        assertThat(data.compositeNonLiquid).isEqualByComparingTo("0")
        verify(assetConfigClient, org.mockito.kotlin.never()).find(any())
    }

    @Test
    fun `convertCurrency FX-scales compositeLiquid and compositeNonLiquid`() {
        // Allocation originally USD; user requests SGD. The composite-split
        // fields must FX-scale along with totalValue / categoryBreakdown —
        // otherwise downstream displays a SGD-labelled response with USD-
        // magnitude composite numbers, surfacing a ~22% understatement of
        // the CPF buckets on Mary's wealth tile.
        val usdAllocation =
            AllocationData(
                totalValue = BigDecimal("288194.11"),
                currency = "USD",
                compositeLiquid = BigDecimal("173940.00"),
                compositeNonLiquid = BigDecimal("45240.00")
            )

        val pair = IsoCurrencyPair(from = "USD", to = "SGD")
        val rate = BigDecimal("1.2873")
        whenever(tokenService.bearerToken).thenReturn("bearer")
        whenever(fxService.getRates(any<FxRequest>(), eq("bearer"))).thenReturn(
            FxResponse(
                FxPairResults(rates = mapOf(pair to FxRate(from = Currency("USD"), to = Currency("SGD"), rate = rate)))
            )
        )

        val result = allocationService.convertCurrency(usdAllocation, "SGD", fxService, "2024-01-15")

        assertThat(result.currency).isEqualTo("SGD")
        assertThat(result.totalValue).isEqualByComparingTo(BigDecimal("370992.28"))
        assertThat(result.compositeLiquid).isEqualByComparingTo(BigDecimal("223912.96"))
        assertThat(result.compositeNonLiquid).isEqualByComparingTo(BigDecimal("58237.45"))
    }
}