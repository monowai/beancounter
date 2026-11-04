package com.beancounter.position.networth

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.client.services.PriceService
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.Currency
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.PriceData
import com.beancounter.common.model.Totals
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.Constants
import com.beancounter.position.composite.AssetConfigClient
import com.beancounter.position.composite.PrivateAssetConfigDto
import com.beancounter.position.composite.SubAccountDto
import com.beancounter.position.utils.TestHelpers
import com.beancounter.position.valuation.AggregateValuationRequest
import com.beancounter.position.valuation.Valuation
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

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

    @Mock
    private lateinit var priceService: PriceService

    private lateinit var netWorthService: NetWorthService

    private val usd = Currency("USD")
    private val asAt = "2024-01-15"
    private val token = "bearer"

    @BeforeEach
    fun setUp() {
        netWorthService =
            NetWorthService(
                valuationService,
                assetConfigClient,
                fxService,
                tokenService,
                WeekChange(priceService, fxService, tokenService, DateUtils())
            )
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
        assertThat(result.gainOnDayPercent).isEqualByComparingTo("0")
        assertThat(result.portfolioCount).isZero()
        assertThat(result.classificationBreakdown).isEmpty()
        assertThat(result.portfolios).isEmpty()
        verify(valuationService, never()).getAggregatedPositions(any())
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
        verify(
            valuationService
        ).getAggregatedPositions(eq(AggregateValuationRequest(listOf(portfolio), asAt, true, "SGD", false)))
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
    fun `should sum gain on day over every position, as the published portfolio figure does`() {
        // MarketValue sets gainOnDay only for a priced, non-cash position with a previous
        // close, so an unpriced position carries zero. A price whose changePercent reads zero
        // still moved by close - previousClose; that move counts, as it does in the totals.
        val portfolio = portfolio("p1", "SGD", marketValue = "3000")
        val priced =
            position(TestHelpers.createTestAsset("priced"), portfolio, "SGD", marketValue = "1000").apply {
                moneyValues[Position.In.PORTFOLIO]!!.gainOnDay = BigDecimal("25")
                moneyValues[Position.In.PORTFOLIO]!!.priceData =
                    PriceData().apply { changePercent = BigDecimal("0.025") }
            }
        val unpriced = position(TestHelpers.createTestAsset("unpriced"), portfolio, "SGD", marketValue = "1000")
        val noReportedChange =
            position(TestHelpers.createTestAsset("flat"), portfolio, "SGD", marketValue = "1000").apply {
                moneyValues[Position.In.PORTFOLIO]!!.gainOnDay = BigDecimal("7")
                moneyValues[Position.In.PORTFOLIO]!!.priceData = PriceData().apply { changePercent = BigDecimal.ZERO }
            }
        val positions = positions(portfolio, "SGD", totalMarketValue = "3000", priced, unpriced, noReportedChange)
        stubAggregated(listOf(portfolio), positions, "SGD")
        whenever(assetConfigClient.findAll()).thenReturn(emptyList())

        val result = netWorthService.calculate(listOf(portfolio), asAt, "SGD")

        assertThat(result.gainOnDay).isEqualByComparingTo("32")
    }

    @Test
    fun `should report gain on day as a percentage of the previous day's net worth`() {
        val portfolio = portfolio("p1", "SGD", marketValue = "2050")
        val priced =
            position(TestHelpers.createTestAsset("priced"), portfolio, "SGD", marketValue = "2050").apply {
                moneyValues[Position.In.PORTFOLIO]!!.gainOnDay = BigDecimal("50")
                moneyValues[Position.In.PORTFOLIO]!!.priceData =
                    PriceData().apply { changePercent = BigDecimal("0.025") }
            }
        stubAggregated(listOf(portfolio), positions(portfolio, "SGD", totalMarketValue = "2050", priced), "SGD")
        whenever(assetConfigClient.findAll()).thenReturn(emptyList())

        val result = netWorthService.calculate(listOf(portfolio), asAt, "SGD")

        assertThat(result.gainOnDayPercent).isEqualByComparingTo("2.50")
    }

    /**
     * A held position priced now at [closeNow] in its own currency (USD), worth [marketValue]
     * in SGD at today's [fxNow].
     */
    private fun held(
        code: String,
        portfolio: Portfolio,
        marketValue: String,
        closeNow: String,
        fxNow: String = "1"
    ): Position =
        position(TestHelpers.createTestAsset(code), portfolio, "SGD", marketValue).apply {
            moneyValues[Position.In.TRADE] =
                MoneyValues(usd).apply { priceData = PriceData().apply { close = BigDecimal(closeNow) } }
            moneyValues[Position.In.PORTFOLIO]!!.priceData =
                PriceData().apply { close = BigDecimal(closeNow).multiply(BigDecimal(fxNow)) }
        }

    private fun stubWeekAgoRate(rate: String) {
        whenever(fxService.getRates(argThat<FxRequest> { rateDate == "2024-01-08" }, eq(token))).thenReturn(
            FxResponse(
                FxPairResults(
                    rates = mapOf(IsoCurrencyPair("USD", "SGD") to FxRate(usd, Currency("SGD"), BigDecimal(rate)))
                )
            )
        )
    }

    private fun stubWeekAgoCloses(vararg closes: Pair<Position, String>) {
        whenever(tokenService.bearerToken).thenReturn(token)
        whenever(priceService.getPrices(argThat<PriceRequest> { date == "2024-01-08" }, eq(token))).thenReturn(
            PriceResponse(
                closes.map { (position, close) ->
                    MarketData(position.asset, priceDate = LocalDate.parse("2024-01-08"), close = BigDecimal(close))
                }
            )
        )
    }

    @Test
    fun `should report the 7 day change of current holdings against their close a week before`() {
        val portfolio = portfolio("p1", "SGD", marketValue = "1100")
        val position = held("weekly", portfolio, marketValue = "1100", closeNow = "110")
        stubAggregated(listOf(portfolio), positions(portfolio, "SGD", totalMarketValue = "1100", position), "SGD")
        whenever(assetConfigClient.findAll()).thenReturn(emptyList())
        stubWeekAgoCloses(position to "100")

        stubWeekAgoRate("1")
        val result = netWorthService.calculate(listOf(portfolio), asAt, "SGD")

        // Worth 1100 now at 110, so 1000 at last week's 100: up 100 on 1000.
        assertThat(result.weekChange).isEqualByComparingTo("100.00")
        assertThat(result.weekChangePercent).isEqualByComparingTo("10.00")
    }

    @Test
    fun `should leave a holding with no close a week before out of the 7 day change`() {
        val portfolio = portfolio("p1", "SGD", marketValue = "2100")
        val priced = held("priced-week", portfolio, marketValue = "1100", closeNow = "110")
        val newer = held("listed-this-week", portfolio, marketValue = "1000", closeNow = "50")
        stubAggregated(
            listOf(portfolio),
            positions(portfolio, "SGD", totalMarketValue = "2100", priced, newer),
            "SGD"
        )
        whenever(assetConfigClient.findAll()).thenReturn(emptyList())
        stubWeekAgoCloses(priced to "100")

        stubWeekAgoRate("1")
        val result = netWorthService.calculate(listOf(portfolio), asAt, "SGD")

        assertThat(result.weekChange).isEqualByComparingTo("100.00")
        assertThat(result.weekChangePercent).isEqualByComparingTo("5.00")
    }

    @Test
    fun `should report the 7 day change as unknown when the week ago prices cannot be read`() {
        val portfolio = portfolio("p1", "SGD", marketValue = "1100")
        val position = held("week-outage", portfolio, marketValue = "1100", closeNow = "110")
        stubAggregated(listOf(portfolio), positions(portfolio, "SGD", totalMarketValue = "1100", position), "SGD")
        whenever(assetConfigClient.findAll()).thenReturn(emptyList())
        whenever(tokenService.bearerToken).thenReturn(token)
        whenever(priceService.getPrices(any(), eq(token))).thenThrow(BusinessException("svc-data unavailable"))

        val result = netWorthService.calculate(listOf(portfolio), asAt, "SGD")

        assertThat(result.holdingsValue).isEqualByComparingTo("1100")
        assertThat(result.weekChange).isNull()
        assertThat(result.weekChangePercent).isNull()
    }

    @Test
    fun `should report the 7 day change as unknown when no holding has a week ago close`() {
        val portfolio = portfolio("p1", "SGD", marketValue = "1100")
        val position = held("week-unpriced", portfolio, marketValue = "1100", closeNow = "110")
        stubAggregated(listOf(portfolio), positions(portfolio, "SGD", totalMarketValue = "1100", position), "SGD")
        whenever(assetConfigClient.findAll()).thenReturn(emptyList())
        stubWeekAgoCloses()

        val result = netWorthService.calculate(listOf(portfolio), asAt, "SGD")

        assertThat(result.weekChangePercent).isNull()
    }

    @Test
    fun `should not ask for week ago prices when nothing held is priced`() {
        val portfolio = portfolio("p1", "SGD", marketValue = "1000")
        val unpriced = position(TestHelpers.createTestAsset("unpriced-week"), portfolio, "SGD", marketValue = "1000")
        stubAggregated(listOf(portfolio), positions(portfolio, "SGD", totalMarketValue = "1000", unpriced), "SGD")
        whenever(assetConfigClient.findAll()).thenReturn(emptyList())

        val result = netWorthService.calculate(listOf(portfolio), asAt, "SGD")

        assertThat(result.weekChange).isNull()
        assertThat(result.weekChangePercent).isNull()
        verify(priceService, never()).getPrices(any(), any())
    }

    @Test
    fun `should include the week's FX move in the 7 day change of a foreign holding`() {
        val portfolio = portfolio("p1", "SGD", marketValue = "1100")
        // 8 units at 110 USD, 1.25 SGD to the USD today: 1100 SGD.
        val position = held("fx-week", portfolio, marketValue = "1100", closeNow = "110", fxNow = "1.25")
        stubAggregated(listOf(portfolio), positions(portfolio, "SGD", totalMarketValue = "1100", position), "SGD")
        whenever(assetConfigClient.findAll()).thenReturn(emptyList())
        stubWeekAgoCloses(position to "100")
        stubWeekAgoRate("1.20")

        val result = netWorthService.calculate(listOf(portfolio), asAt, "SGD")

        // A week ago: 8 x 100 USD x 1.20 = 960 SGD. Up 140, not the 100 the price alone gives.
        assertThat(result.weekChange).isEqualByComparingTo("140.00")
    }

    @Test
    fun `should report the 7 day change as unknown when the week ago FX rate cannot be read`() {
        val portfolio = portfolio("p1", "SGD", marketValue = "1100")
        val position = held("fx-outage", portfolio, marketValue = "1100", closeNow = "110", fxNow = "1.25")
        stubAggregated(listOf(portfolio), positions(portfolio, "SGD", totalMarketValue = "1100", position), "SGD")
        whenever(assetConfigClient.findAll()).thenReturn(emptyList())
        stubWeekAgoCloses(position to "100")
        whenever(fxService.getRates(any<FxRequest>(), eq(token))).thenThrow(BusinessException("svc-data unavailable"))

        val result = netWorthService.calculate(listOf(portfolio), asAt, "SGD")

        assertThat(result.holdingsValue).isEqualByComparingTo("1100")
        assertThat(result.weekChange).isNull()
    }

    @Test
    fun `should leave the 7 day change unknown, not fail the headline, when asAt cannot be parsed`() {
        val badAsAt = "not-a-date"
        val portfolio = portfolio("p1", "SGD", marketValue = "1100")
        val position = held("bad-date", portfolio, marketValue = "1100", closeNow = "110")
        stubAggregated(
            listOf(portfolio),
            positions(portfolio, "SGD", totalMarketValue = "1100", position),
            "SGD",
            badAsAt
        )
        whenever(assetConfigClient.findAll()).thenReturn(emptyList())

        val result = netWorthService.calculate(listOf(portfolio), badAsAt, "SGD")

        assertThat(result.holdingsValue).isEqualByComparingTo("1100")
        assertThat(result.weekChange).isNull()
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
                TestHelpers.createTestAsset("misc").apply { reportCategory = "Collectible" },
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

    @Test
    fun `should convert each portfolio's gain on day from base currency`() {
        val sgdPortfolio = portfolio("p-sgd", "SGD", marketValue = "37000", gainOnDay = "-150")
        val usdPortfolio = portfolio("p-usd", "USD", marketValue = "40000", gainOnDay = "250")
        val unvalued = portfolio("p-new", "SGD", marketValue = "0")
        val portfolios = listOf(sgdPortfolio, usdPortfolio, unvalued)
        stubAggregated(portfolios, positions(sgdPortfolio, "SGD", totalMarketValue = "88200"), "SGD")
        whenever(assetConfigClient.findAll()).thenReturn(emptyList())
        stubRates(mapOf(IsoCurrencyPair("USD", "SGD") to "1.28"))

        val rows = netWorthService.calculate(portfolios, asAt, "SGD").portfolios.associateBy { it.id }

        assertThat(rows["p-sgd"]!!.gainOnDay).isEqualByComparingTo("-150.00")
        assertThat(rows["p-usd"]!!.gainOnDay).isEqualByComparingTo("320.00")
        assertThat(rows["p-new"]!!.gainOnDay).isEqualByComparingTo("0")
    }

    @Test
    fun `should report each portfolio's gain on day as a percentage of its previous value`() {
        val sgdPortfolio = portfolio("p-sgd", "SGD", marketValue = "37000", gainOnDay = "-150")
        val usdPortfolio = portfolio("p-usd", "USD", marketValue = "40000", gainOnDay = "250")
        val unvalued = portfolio("p-new", "SGD", marketValue = "0")
        val portfolios = listOf(sgdPortfolio, usdPortfolio, unvalued)
        stubAggregated(portfolios, positions(sgdPortfolio, "SGD", totalMarketValue = "88200"), "SGD")
        whenever(assetConfigClient.findAll()).thenReturn(emptyList())
        stubRates(mapOf(IsoCurrencyPair("USD", "SGD") to "1.28"))

        val rows = netWorthService.calculate(portfolios, asAt, "SGD").portfolios.associateBy { it.id }

        // -150 / (37000 + 150) and 320 / (51200 - 320), in percentage points.
        assertThat(rows["p-sgd"]!!.gainOnDayPercent).isEqualByComparingTo("-0.40")
        assertThat(rows["p-usd"]!!.gainOnDayPercent).isEqualByComparingTo("0.63")
        assertThat(rows["p-new"]!!.gainOnDayPercent).isEqualByComparingTo("0")
    }

    @Test
    fun `should group index assets as investment`() {
        val portfolio = portfolio("p1", "SGD", marketValue = "1000")
        val index =
            position(
                TestHelpers.createTestAsset("idx").apply { reportCategory = AssetCategory.REPORT_INDEX },
                portfolio,
                "SGD",
                marketValue = "1000"
            )
        stubAggregated(listOf(portfolio), positions(portfolio, "SGD", totalMarketValue = "1000", index), "SGD")
        whenever(assetConfigClient.findAll()).thenReturn(emptyList())

        val result = netWorthService.calculate(listOf(portfolio), asAt, "SGD")

        assertThat(result.classificationBreakdown.map { it.classification }).containsExactly("Investment")
        assertThat(result.classificationBreakdown.first().value).isEqualByComparingTo("1000")
    }

    @Test
    fun `should keep total value equal to the sum of its rounded components`() {
        // money(0.005) + money(0.005) = 0.02, but money(0.005 + 0.005) = 0.01.
        val portfolio = portfolio("p1", "SGD", marketValue = "0.01")
        stubAggregated(listOf(portfolio), positions(portfolio, "SGD", totalMarketValue = "0.005"), "SGD")
        whenever(assetConfigClient.findAll())
            .thenReturn(listOf(config("cpf", "SGD", SubAccountDto(code = "OA", balance = BigDecimal("0.005")))))

        val result = netWorthService.calculate(listOf(portfolio), asAt, "SGD")

        assertThat(result.holdingsValue).isEqualByComparingTo("0.01")
        assertThat(result.standaloneCompositeValue).isEqualByComparingTo("0.01")
        assertThat(result.totalValue).isEqualByComparingTo(result.holdingsValue.add(result.standaloneCompositeValue))
        assertThat(result.portfolios.single().percentage).isEqualByComparingTo("50.00")
    }

    private fun portfolio(
        id: String,
        currencyCode: String,
        marketValue: String,
        irr: String = "0",
        gainOnDay: String? = null
    ): Portfolio =
        Portfolio(
            id = id,
            code = id.uppercase(),
            name = "Portfolio $id",
            marketValue = BigDecimal(marketValue),
            irr = BigDecimal(irr),
            gainOnDay = gainOnDay?.let(::BigDecimal),
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
        currency: String,
        valuationDate: String = asAt
    ) {
        whenever(
            valuationService.getAggregatedPositions(
                eq(AggregateValuationRequest(portfolios, valuationDate, true, currency, false))
            )
        ).thenReturn(PositionResponse(positions))
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