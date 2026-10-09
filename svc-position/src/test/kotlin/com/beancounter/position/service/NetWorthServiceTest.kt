package com.beancounter.position.service

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.Currency
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.PriceData
import com.beancounter.common.model.Totals
import com.beancounter.position.Constants
import com.beancounter.position.composite.AssetConfigClient
import com.beancounter.position.composite.PrivateAssetConfigDto
import com.beancounter.position.composite.SubAccountDto
import com.beancounter.position.utils.TestHelpers
import com.beancounter.position.valuation.Valuation
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

/**
 * Unit tests for [NetWorthService] — the server-side port of bc-view's
 * useWealthSummary / useNetWorthData derivation.
 */
@ExtendWith(MockitoExtension::class)
class NetWorthServiceTest {
    @Mock
    private lateinit var valuationService: Valuation

    @Mock
    private lateinit var assetConfigClient: AssetConfigClient

    @Mock
    private lateinit var fxService: FxService

    @Mock
    private lateinit var tokenService: TokenService

    private lateinit var netWorthService: NetWorthService

    private val sgd = Currency("SGD")
    private val usd = Currency("USD")
    private val asAt = "2024-01-15"
    private val token = "bearer"

    @BeforeEach
    fun setUp() {
        netWorthService = NetWorthService(valuationService, assetConfigClient, fxService, tokenService)
    }

    @Test
    fun `should return zeros when there are no portfolios and no composite configs`() {
        whenever(assetConfigClient.findAll()).thenReturn(emptyList())

        val result = netWorthService.calculate(emptyList(), asAt, "SGD")

        assertThat(result.asAt).isEqualTo(asAt)
        assertThat(result.currency).isEqualTo("SGD")
        assertThat(result.totalValue).isEqualByComparingTo("0")
        assertThat(result.holdingsValue).isEqualByComparingTo("0")
        assertThat(result.standaloneCompositeValue).isEqualByComparingTo("0")
        assertThat(result.healthcareReserve).isEqualByComparingTo("0")
        assertThat(result.gainOnDay).isEqualByComparingTo("0")
        assertThat(result.portfolioCount).isZero()
        assertThat(result.classificationBreakdown).isEmpty()
        assertThat(result.portfolios).isEmpty()
        verify(valuationService, never()).getAggregatedPositions(any(), any(), any(), any())
        verify(fxService, never()).getRates(any(), any())
    }

    @Test
    fun `should total live aggregated holdings in the target currency`() {
        val portfolio = portfolio("p1", "SGD", marketValue = "36000")
        val positions = positions(portfolio, "SGD", totalMarketValue = "37000")
        stubAggregated(listOf(portfolio), positions, "SGD")
        whenever(assetConfigClient.findAll()).thenReturn(emptyList())

        val result = netWorthService.calculate(listOf(portfolio), asAt, "SGD")

        // Live aggregated total wins over the persisted portfolio.marketValue.
        assertThat(result.holdingsValue).isEqualByComparingTo("37000")
        assertThat(result.totalValue).isEqualByComparingTo("37000")
        assertThat(result.portfolioCount).isEqualTo(1)
        verify(valuationService).getAggregatedPositions(eq(listOf(portfolio)), eq(asAt), eq(true), eq("SGD"))
        verify(fxService, never()).getRates(any(), any())
    }

    @Test
    fun `should add standalone composite balances that have no parent position`() {
        val portfolio = portfolio("p1", "SGD", marketValue = "37000")
        val positions = positions(portfolio, "SGD", totalMarketValue = "37000")
        stubAggregated(listOf(portfolio), positions, "SGD")
        whenever(assetConfigClient.findAll()).thenReturn(
            listOf(
                config(
                    "cpf-standalone",
                    "SGD",
                    SubAccountDto(code = "OA", balance = BigDecimal("60000")),
                    SubAccountDto(code = "SA", balance = BigDecimal("40000"))
                )
            )
        )

        val result = netWorthService.calculate(listOf(portfolio), asAt, "SGD")

        assertThat(result.standaloneCompositeValue).isEqualByComparingTo("100000")
        assertThat(result.totalValue).isEqualByComparingTo("137000")
    }

    @Test
    fun `should not add composite balances whose parent asset is held`() {
        val portfolio = portfolio("p1", "SGD", marketValue = "276000")
        val cpfAsset =
            TestHelpers.createTestAsset("cpf-held").apply {
                reportCategory =
                    AssetCategory.REPORT_RETIREMENT_FUND
            }
        val cpfPosition = position(cpfAsset, portfolio, "SGD", marketValue = "276000")
        val positions = positions(portfolio, "SGD", totalMarketValue = "276000", cpfPosition)
        stubAggregated(listOf(portfolio), positions, "SGD")
        whenever(assetConfigClient.findAll()).thenReturn(
            listOf(
                config(
                    "cpf-held",
                    "SGD",
                    SubAccountDto(code = "OA", balance = BigDecimal("218000")),
                    SubAccountDto(code = "MA", balance = BigDecimal("58000"))
                )
            )
        )

        val result = netWorthService.calculate(listOf(portfolio), asAt, "SGD")

        // The parent position already carries the balance via CompositeValuation.
        assertThat(result.standaloneCompositeValue).isEqualByComparingTo("0")
        assertThat(result.totalValue).isEqualByComparingTo("276000")
    }

    @Test
    fun `should report healthcare reserve without adding it to total value`() {
        val portfolio = portfolio("p1", "SGD", marketValue = "363000")
        val positions = positions(portfolio, "SGD", totalMarketValue = "363000")
        stubAggregated(listOf(portfolio), positions, "SGD")
        whenever(assetConfigClient.findAll()).thenReturn(
            listOf(
                config(
                    "cpf-standalone",
                    "SGD",
                    SubAccountDto(code = "MA", balance = BigDecimal("58000")),
                    SubAccountDto(code = "OA", balance = BigDecimal.ZERO)
                )
            )
        )

        val result = netWorthService.calculate(listOf(portfolio), asAt, "SGD")

        assertThat(result.healthcareReserve).isEqualByComparingTo("58000")
        assertThat(result.standaloneCompositeValue).isEqualByComparingTo("0")
        assertThat(result.totalValue).isEqualByComparingTo("363000")
    }

    @Test
    fun `should convert composite balances using the config currency rate`() {
        val portfolio = portfolio("p1", "SGD", marketValue = "0")
        val positions = positions(portfolio, "SGD", totalMarketValue = "0")
        stubAggregated(listOf(portfolio), positions, "SGD")
        whenever(assetConfigClient.findAll()).thenReturn(
            listOf(
                config(
                    "us-pension",
                    "USD",
                    SubAccountDto(code = "401K", balance = BigDecimal("1000")),
                    SubAccountDto(code = "MA", balance = BigDecimal("500"))
                )
            )
        )
        stubRates(mapOf(IsoCurrencyPair("USD", "SGD") to "1.28"))

        val result = netWorthService.calculate(listOf(portfolio), asAt, "SGD")

        assertThat(result.standaloneCompositeValue).isEqualByComparingTo("1280.00")
        assertThat(result.healthcareReserve).isEqualByComparingTo("640.00")
        assertThat(result.totalValue).isEqualByComparingTo("1280.00")
        val captor = argumentCaptor<FxRequest>()
        verify(fxService).getRates(captor.capture(), eq(token))
        assertThat(captor.firstValue.rateDate).isEqualTo(asAt)
        assertThat(captor.firstValue.pairs).containsExactly(IsoCurrencyPair("USD", "SGD"))
    }

    @Test
    fun `should fail when a required fx rate is missing`() {
        val portfolio = portfolio("p1", "SGD", marketValue = "0")
        val positions = positions(portfolio, "SGD", totalMarketValue = "0")
        stubAggregated(listOf(portfolio), positions, "SGD")
        whenever(assetConfigClient.findAll()).thenReturn(
            listOf(config("thb-policy", "THB", SubAccountDto(code = "POL", balance = BigDecimal("500"))))
        )
        stubRates(emptyMap())

        assertThatThrownBy { netWorthService.calculate(listOf(portfolio), asAt, "SGD") }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("THB:SGD")
    }

    @Test
    fun `should sum gain on day only for positions with price change data`() {
        val portfolio = portfolio("p1", "SGD", marketValue = "3000")
        val priced =
            position(TestHelpers.createTestAsset("priced"), portfolio, "SGD", marketValue = "1000").apply {
                moneyValues[Position.In.PORTFOLIO]!!.gainOnDay = BigDecimal("25")
                moneyValues[Position.In.PORTFOLIO]!!.priceData =
                    PriceData().apply { changePercent = BigDecimal("0.025") }
            }
        val unpriced =
            position(TestHelpers.createTestAsset("unpriced"), portfolio, "SGD", marketValue = "1000").apply {
                moneyValues[Position.In.PORTFOLIO]!!.gainOnDay = BigDecimal("999")
            }
        val flat =
            position(TestHelpers.createTestAsset("flat"), portfolio, "SGD", marketValue = "1000").apply {
                moneyValues[Position.In.PORTFOLIO]!!.gainOnDay = BigDecimal("7")
                moneyValues[Position.In.PORTFOLIO]!!.priceData = PriceData().apply { changePercent = BigDecimal.ZERO }
            }
        val positions = positions(portfolio, "SGD", totalMarketValue = "3000", priced, unpriced, flat)
        stubAggregated(listOf(portfolio), positions, "SGD")
        whenever(assetConfigClient.findAll()).thenReturn(emptyList())

        val result = netWorthService.calculate(listOf(portfolio), asAt, "SGD")

        assertThat(result.gainOnDay).isEqualByComparingTo("25")
    }

    @Test
    fun `should group classification breakdown into liquidity groups with percentages`() {
        val portfolio = portfolio("p1", "SGD", marketValue = "4000")
        val equity =
            position(
                TestHelpers.createTestAsset("eq").apply { reportCategory = AssetCategory.REPORT_EQUITY },
                portfolio,
                "SGD",
                marketValue = "1000"
            )
        val etf =
            position(
                TestHelpers.createTestAsset("etf").apply { reportCategory = AssetCategory.REPORT_ETF },
                portfolio,
                "SGD",
                marketValue = "1000"
            )
        val cash =
            position(
                TestHelpers.createTestAsset("cash").apply { reportCategory = AssetCategory.REPORT_CASH },
                portfolio,
                "SGD",
                marketValue = "1000"
            )
        val property =
            position(
                TestHelpers.createTestAsset("home").apply { reportCategory = AssetCategory.REPORT_PROPERTY },
                portfolio,
                "SGD",
                marketValue = "500"
            )
        val pension =
            position(
                TestHelpers.createTestAsset("cpf").apply { reportCategory = AssetCategory.REPORT_RETIREMENT_FUND },
                portfolio,
                "SGD",
                marketValue = "250"
            )
        val other =
            position(
                TestHelpers.createTestAsset("idx").apply { reportCategory = AssetCategory.REPORT_INDEX },
                portfolio,
                "SGD",
                marketValue = "250"
            )
        val positions =
            positions(portfolio, "SGD", totalMarketValue = "4000", equity, etf, cash, property, pension, other)
        stubAggregated(listOf(portfolio), positions, "SGD")
        whenever(assetConfigClient.findAll()).thenReturn(emptyList())

        val result = netWorthService.calculate(listOf(portfolio), asAt, "SGD")

        assertThat(result.classificationBreakdown.map { it.classification })
            .containsExactly("Investment", "Cash", "Property", "Retirement", "Other")
        val byGroup = result.classificationBreakdown.associateBy { it.classification }
        assertThat(byGroup["Investment"]!!.value).isEqualByComparingTo("2000")
        assertThat(byGroup["Investment"]!!.percentage).isEqualByComparingTo("50.00")
        assertThat(byGroup["Cash"]!!.percentage).isEqualByComparingTo("25.00")
        assertThat(byGroup["Property"]!!.percentage).isEqualByComparingTo("12.50")
        assertThat(byGroup["Retirement"]!!.percentage).isEqualByComparingTo("6.25")
        assertThat(byGroup["Other"]!!.percentage).isEqualByComparingTo("6.25")
    }

    @Test
    fun `should convert portfolio rows from base currency and compute percentage of total`() {
        val sgdPortfolio = portfolio("p-sgd", "SGD", marketValue = "37000", irr = "0.05")
        val usdPortfolio = portfolio("p-usd", "USD", marketValue = "40000", irr = "0.10")
        // Aggregated total already in SGD: 37000 + 40000 * 1.28
        val positions = positions(sgdPortfolio, "SGD", totalMarketValue = "88200")
        stubAggregated(listOf(sgdPortfolio, usdPortfolio), positions, "SGD")
        whenever(assetConfigClient.findAll()).thenReturn(emptyList())
        stubRates(mapOf(IsoCurrencyPair("USD", "SGD") to "1.28"))

        val result = netWorthService.calculate(listOf(sgdPortfolio, usdPortfolio), asAt, "SGD")

        assertThat(result.portfolioCount).isEqualTo(2)
        assertThat(result.totalValue).isEqualByComparingTo("88200")
        val rows = result.portfolios.associateBy { it.id }
        assertThat(rows["p-sgd"]!!.code).isEqualTo("P-SGD")
        assertThat(rows["p-sgd"]!!.name).isEqualTo("Portfolio p-sgd")
        assertThat(rows["p-sgd"]!!.value).isEqualByComparingTo("37000.00")
        assertThat(rows["p-sgd"]!!.percentage).isEqualByComparingTo("41.95")
        assertThat(rows["p-sgd"]!!.irr).isEqualByComparingTo("0.05")
        assertThat(rows["p-usd"]!!.value).isEqualByComparingTo("51200.00")
        assertThat(rows["p-usd"]!!.percentage).isEqualByComparingTo("58.05")
        assertThat(rows["p-usd"]!!.irr).isEqualByComparingTo("0.10")
        val captor = argumentCaptor<FxRequest>()
        verify(fxService).getRates(captor.capture(), eq(token))
        assertThat(captor.firstValue.pairs).containsExactly(IsoCurrencyPair("USD", "SGD"))
    }

    private fun portfolio(
        id: String,
        currencyCode: String,
        marketValue: String,
        irr: String = "0"
    ): Portfolio =
        Portfolio(
            id = id,
            code = id.uppercase(),
            name = "Portfolio $id",
            marketValue = BigDecimal(marketValue),
            irr = BigDecimal(irr),
            currency = Currency(currencyCode),
            base = Currency(currencyCode),
            owner = Constants.owner
        )

    private fun position(
        asset: com.beancounter.common.model.Asset,
        portfolio: Portfolio,
        currencyCode: String,
        marketValue: String
    ): Position =
        Position(asset = asset, portfolio = portfolio).apply {
            moneyValues[Position.In.PORTFOLIO] =
                MoneyValues(Currency(currencyCode)).apply { this.marketValue = BigDecimal(marketValue) }
        }

    private fun positions(
        portfolio: Portfolio,
        currencyCode: String,
        totalMarketValue: String,
        vararg items: Position
    ): Positions {
        val positions = Positions(portfolio)
        items.forEach { positions.add(it) }
        positions.totals[Position.In.PORTFOLIO] =
            Totals(currency = Currency(currencyCode), marketValue = BigDecimal(totalMarketValue))
        return positions
    }

    private fun config(
        assetId: String,
        rentalCurrency: String,
        vararg subAccounts: SubAccountDto
    ): PrivateAssetConfigDto =
        PrivateAssetConfigDto(
            assetId = assetId,
            rentalCurrency = rentalCurrency,
            subAccounts = subAccounts.toList()
        )

    private fun stubAggregated(
        portfolios: List<Portfolio>,
        positions: Positions,
        currency: String
    ) {
        whenever(valuationService.getAggregatedPositions(eq(portfolios), eq(asAt), eq(true), eq(currency)))
            .thenReturn(PositionResponse(positions))
    }

    private fun stubRates(rates: Map<IsoCurrencyPair, String>) {
        whenever(tokenService.bearerToken).thenReturn(token)
        whenever(fxService.getRates(any<FxRequest>(), eq(token))).thenReturn(
            FxResponse(
                FxPairResults(
                    rates =
                        rates.mapValues { (pair, rate) ->
                            FxRate(from = Currency(pair.from), to = Currency(pair.to), rate = BigDecimal(rate))
                        }
                )
            )
        )
    }
}