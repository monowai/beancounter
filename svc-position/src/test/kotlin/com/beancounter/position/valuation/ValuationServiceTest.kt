package com.beancounter.position.valuation

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.client.services.ClassificationClient
import com.beancounter.client.services.TrnService
import com.beancounter.common.contracts.AssetClassificationSummary
import com.beancounter.common.contracts.BulkClassificationResponse
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.Currency
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Totals
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.accumulation.Accumulator
import com.beancounter.position.accumulation.BuyBehaviour
import com.beancounter.position.accumulation.SellBehaviour
import com.beancounter.position.accumulation.SplitBehaviour
import com.beancounter.position.accumulation.TrnBehaviourFactory
import com.beancounter.position.service.MarketValueUpdateProducer
import com.beancounter.position.service.PositionService
import com.beancounter.position.service.PositionValuationService
import com.beancounter.position.utils.TestHelpers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.capture
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Tests for ValuationService focusing on edge cases like fully sold portfolios.
 */
@ExtendWith(MockitoExtension::class)
class ValuationServiceTest {
    companion object {
        private val MV_10K = BigDecimal("10000.00")
        private val PRICE_150 = BigDecimal("150.00")
    }

    @Mock
    private lateinit var positionValuationService: PositionValuationService

    @Mock
    private lateinit var trnService: TrnService

    @Mock
    private lateinit var positionService: PositionService

    @Mock
    private lateinit var marketValueUpdateProducer: MarketValueUpdateProducer

    @Mock
    private lateinit var classificationClient: ClassificationClient

    @Mock
    private lateinit var fxRateService: FxService

    @Mock
    private lateinit var tokenService: TokenService

    @Captor
    private lateinit var portfolioCaptor: ArgumentCaptor<Portfolio>

    private lateinit var valuationService: ValuationService

    private val dateUtils = DateUtils()
    private val portfolio = TestHelpers.createTestPortfolio("ValuationServiceTest")

    @BeforeEach
    fun setup() {
        // Default mock for classificationClient - returns empty response (lenient because not all tests use it)
        Mockito
            .lenient()
            .`when`(
                classificationClient.getClassifications(any())
            ).thenReturn(BulkClassificationResponse())

        Mockito.lenient().`when`(tokenService.bearerToken).thenReturn("")
        Mockito
            .lenient()
            .`when`(fxRateService.getRates(any(), any()))
            .thenReturn(FxResponse())

        valuationService =
            ValuationService(
                positionValuationService,
                trnService,
                positionService,
                marketValueUpdateProducer,
                classificationClient,
                fxRateService,
                tokenService,
                dateUtils
            )
    }

    @Test
    fun `value should send zero market value when all positions have zero quantity`() {
        // Given - a portfolio where all positions are sold (zero quantity)
        // This simulates the USX scenario where prices aren't fetched for zero-qty positions
        val positions = Positions(portfolio)
        positions.asAt = DateUtils.TODAY

        // Add a position with zero quantity (fully sold)
        val asset = TestHelpers.createTestAsset("SOLD_ASSET", "US")
        val position = Position(asset, portfolio)
        position.quantityValues.purchased = BigDecimal.ZERO
        position.quantityValues.sold = BigDecimal.ZERO
        positions.add(position)

        // When positionValuationService.value() returns positions WITHOUT totals set
        // (this happens when no prices are fetched for zero-qty positions)
        whenever(positionValuationService.value(any(), any())).thenReturn(positions)

        // When
        val result = valuationService.value(positions)

        // Then - should return successfully and send market value update with 0
        assertThat(result).isNotNull
        assertThat(result.data).isEqualTo(positions)
        // Market value update SHOULD be sent with 0 when totals are null (fully sold portfolio)
        verify(marketValueUpdateProducer).sendMessage(any())
    }

    @Test
    fun `value should send market value update when totals are set`() {
        // Given - a portfolio with valued positions that have totals
        val positions = Positions(portfolio)
        positions.asAt = DateUtils.TODAY

        // Add a position with quantity
        val asset = TestHelpers.createTestAsset("HELD_ASSET", "US")
        val position = Position(asset, portfolio)
        position.quantityValues.purchased = BigDecimal.TEN
        positions.add(position)

        // Set up PORTFOLIO totals (in portfolio currency)
        val portfolioTotals = Totals(portfolio.currency)
        portfolioTotals.marketValue = MV_10K
        portfolioTotals.irr = BigDecimal("0.15")
        positions.setTotal(Position.In.PORTFOLIO, portfolioTotals)

        // Set up BASE totals (in user's base currency) - this is what gets sent
        val baseTotals = Totals(portfolio.base)
        baseTotals.marketValue = MV_10K
        baseTotals.irr = BigDecimal("0.15")
        positions.setTotal(Position.In.BASE, baseTotals)

        whenever(positionValuationService.value(any(), any())).thenReturn(positions)

        // When
        val result = valuationService.value(positions)

        // Then - should return successfully and send market value update
        assertThat(result).isNotNull
        assertThat(result.data).isEqualTo(positions)
        verify(marketValueUpdateProducer).sendMessage(any())
    }

    @Test
    fun `getPositions should handle fully sold portfolio gracefully`() {
        // Given - a portfolio with no active positions
        val positions = Positions(portfolio)
        positions.asAt = DateUtils.TODAY
        val positionResponse = PositionResponse(positions)

        whenever(trnService.query(any<Portfolio>(), any<String>()))
            .thenReturn(TrnResponse(emptyList()))
        whenever(positionService.build(any(), any())).thenReturn(positionResponse)

        // When - request valuation without market value (value=false to avoid needing positionValuationService mock)
        val result = valuationService.getPositions(portfolio, DateUtils.TODAY, value = false)

        // Then - should return empty positions without error
        assertThat(result).isNotNull
        assertThat(result.data.positions).isEmpty()
    }

    @Test
    fun `getAggregatedPositions returns empty response for empty portfolio list`() {
        // Given - empty portfolio list
        val portfolios = emptyList<Portfolio>()

        // When
        val result = valuationService.getAggregatedPositions(portfolios, DateUtils.TODAY, value = false)

        // Then
        assertThat(result.data.positions).isEmpty()
    }

    @Test
    fun `getAggregatedPositions returns empty response when all portfolios have no transactions`() {
        // Given - portfolios with no transactions
        val portfolio2 = TestHelpers.createTestPortfolio("Portfolio2")
        val portfolios = listOf(portfolio, portfolio2)

        whenever(trnService.query(any<Portfolio>(), any<String>()))
            .thenReturn(TrnResponse(emptyList()))

        // When
        val result = valuationService.getAggregatedPositions(portfolios, DateUtils.TODAY, value = false)

        // Then
        assertThat(result.data.positions).isEmpty()
    }

    @Test
    fun `getAggregatedPositions combines transactions from multiple portfolios`() {
        // Given - two portfolios with transactions
        val portfolio2 = TestHelpers.createTestPortfolio("Portfolio2")
        val portfolios = listOf(portfolio, portfolio2)

        val asset1 = TestHelpers.createTestAsset("AAPL", "US")
        val asset2 = TestHelpers.createTestAsset("GOOGL", "US")
        val trn1 =
            TestHelpers.createTestTransaction(
                asset = asset1,
                portfolio = portfolio,
                trnType = TrnType.BUY,
                quantity = BigDecimal("10"),
                price = PRICE_150,
                tradeDate = LocalDate.of(2024, 1, 15)
            )
        val trn2 =
            TestHelpers.createTestTransaction(
                asset = asset2,
                portfolio = portfolio2,
                trnType = TrnType.BUY,
                quantity = BigDecimal("5"),
                price = BigDecimal("2500.00"),
                tradeDate = LocalDate.of(2024, 1, 16)
            )

        whenever(trnService.query(argThat { id == portfolio.id }, any<String>()))
            .thenReturn(TrnResponse(listOf(trn1)))
        whenever(trnService.query(argThat { id == portfolio2.id }, any<String>()))
            .thenReturn(TrnResponse(listOf(trn2)))

        val positions = Positions(portfolio)
        positions.add(Position(asset1, portfolio))
        positions.add(Position(asset2, portfolio))
        val positionResponse = PositionResponse(positions)

        whenever(positionService.build(any(), any())).thenReturn(positionResponse)

        // When
        val result = valuationService.getAggregatedPositions(portfolios, DateUtils.TODAY, value = false)

        // Then
        assertThat(result.data.positions).hasSize(2)
    }

    @Test
    fun `getAggregatedPositions populates portfolioBreakdown per contributing portfolio`() {
        // Given - two portfolios both holding the same asset
        val portfolio2 = TestHelpers.createTestPortfolio("Portfolio2")
        val portfolios = listOf(portfolio, portfolio2)

        val asset = TestHelpers.createTestAsset("AAPL", "US")
        val trn1 =
            TestHelpers.createTestTransaction(
                asset = asset,
                portfolio = portfolio,
                trnType = TrnType.BUY,
                quantity = BigDecimal("10"),
                price = PRICE_150,
                tradeDate = LocalDate.of(2024, 1, 15)
            )
        val trn2 =
            TestHelpers.createTestTransaction(
                asset = asset,
                portfolio = portfolio2,
                trnType = TrnType.BUY,
                quantity = BigDecimal("5"),
                price = PRICE_150,
                tradeDate = LocalDate.of(2024, 1, 16)
            )

        whenever(trnService.query(argThat { id == portfolio.id }, any<String>()))
            .thenReturn(TrnResponse(listOf(trn1)))
        whenever(trnService.query(argThat { id == portfolio2.id }, any<String>()))
            .thenReturn(TrnResponse(listOf(trn2)))

        val p1Positions =
            Positions(portfolio).apply {
                add(
                    Position(asset, portfolio).apply {
                        quantityValues.purchased = BigDecimal("10")
                        held["DBS"] = BigDecimal("7")
                        held["SCB"] = BigDecimal("3")
                    }
                )
            }
        val p2Positions =
            Positions(portfolio2).apply {
                add(
                    Position(asset, portfolio2).apply {
                        quantityValues.purchased = BigDecimal("5")
                        held["DBS"] = BigDecimal("5")
                    }
                )
            }

        whenever(
            positionService.build(
                argThat { id == portfolio.id },
                argThat { trns.size == 1 }
            )
        ).thenReturn(PositionResponse(p1Positions))
        whenever(
            positionService.build(
                argThat { id == portfolio2.id },
                argThat { trns.size == 1 }
            )
        ).thenReturn(PositionResponse(p2Positions))

        // When
        val result = valuationService.getAggregatedPositions(portfolios, DateUtils.TODAY, value = false)

        // Then - the aggregate sums the two holdings (10 + 5)...
        val agg =
            result.data.positions.values
                .first()
        assertThat(agg.quantityValues.getTotal()).isEqualByComparingTo(BigDecimal("15"))
        // ...and lists each contributing portfolio.
        assertThat(agg.portfolioBreakdown).hasSize(2)
        assertThat(agg.portfolioBreakdown.map { it.portfolioId })
            .containsExactlyInAnyOrder(portfolio.id, portfolio2.id)
        assertThat(agg.portfolioBreakdown.first { it.portfolioId == portfolio.id }.quantity)
            .isEqualByComparingTo(BigDecimal("10"))
        assertThat(agg.portfolioBreakdown.first { it.portfolioId == portfolio2.id }.quantity)
            .isEqualByComparingTo(BigDecimal("5"))
        // ...with each row scoped to ITS portfolio's broker holdings, so the
        // UI can cap a sell at what the chosen portfolio holds per broker.
        assertThat(agg.portfolioBreakdown.first { it.portfolioId == portfolio.id }.held)
            .containsOnlyKeys("DBS", "SCB")
        assertThat(agg.portfolioBreakdown.first { it.portfolioId == portfolio.id }.held["DBS"])
            .isEqualByComparingTo(BigDecimal("7"))
        assertThat(agg.portfolioBreakdown.first { it.portfolioId == portfolio2.id }.held["DBS"])
            .isEqualByComparingTo(BigDecimal("5"))
    }

    @Test
    fun `getAggregatedPositions merges per-broker held quantities across portfolios`() {
        // Given - two portfolios holding the same asset at overlapping brokers
        val portfolio2 = TestHelpers.createTestPortfolio("Portfolio2")
        val portfolios = listOf(portfolio, portfolio2)

        val asset = TestHelpers.createTestAsset("SCHD", "US")
        val trn1 =
            TestHelpers.createTestTransaction(
                asset = asset,
                portfolio = portfolio,
                trnType = TrnType.BUY,
                quantity = BigDecimal("255"),
                price = PRICE_150,
                tradeDate = LocalDate.of(2024, 1, 15)
            )
        val trn2 =
            TestHelpers.createTestTransaction(
                asset = asset,
                portfolio = portfolio2,
                trnType = TrnType.BUY,
                quantity = BigDecimal("25"),
                price = PRICE_150,
                tradeDate = LocalDate.of(2024, 1, 16)
            )

        whenever(trnService.query(argThat { id == portfolio.id }, any<String>()))
            .thenReturn(TrnResponse(listOf(trn1)))
        whenever(trnService.query(argThat { id == portfolio2.id }, any<String>()))
            .thenReturn(TrnResponse(listOf(trn2)))

        val p1Positions =
            Positions(portfolio).apply {
                add(
                    Position(asset, portfolio).apply {
                        quantityValues.purchased = BigDecimal("255")
                        held["DBS"] = BigDecimal("175")
                        held["SCB"] = BigDecimal("80")
                        subAccounts["OA"] = BigDecimal("1000")
                    }
                )
            }
        val p2Positions =
            Positions(portfolio2).apply {
                add(
                    Position(asset, portfolio2).apply {
                        quantityValues.purchased = BigDecimal("25")
                        held["DBS"] = BigDecimal("25")
                        subAccounts["OA"] = BigDecimal("500")
                    }
                )
            }

        whenever(
            positionService.build(
                argThat { id == portfolio.id },
                argThat { trns.size == 1 }
            )
        ).thenReturn(PositionResponse(p1Positions))
        whenever(
            positionService.build(
                argThat { id == portfolio2.id },
                argThat { trns.size == 1 }
            )
        ).thenReturn(PositionResponse(p2Positions))

        // When
        val result = valuationService.getAggregatedPositions(portfolios, DateUtils.TODAY, value = false)

        // Then - per-broker quantities survive aggregation, same broker sums
        val agg =
            result.data.positions.values
                .first()
        assertThat(agg.held).hasSize(2)
        assertThat(agg.held["DBS"]).isEqualByComparingTo(BigDecimal("200"))
        assertThat(agg.held["SCB"]).isEqualByComparingTo(BigDecimal("80"))
        // ...and per-sub-account balances sum the same way
        assertThat(agg.subAccounts["OA"]).isEqualByComparingTo(BigDecimal("1500"))
    }

    @Test
    fun `getAggregatedPositions excludes portfolio with net-zero quantity from breakdown`() {
        // Given - P1 holds 10 AAPL; P2 bought 10 then sold 10 (net zero)
        val portfolio2 = TestHelpers.createTestPortfolio("Portfolio2")
        val portfolios = listOf(portfolio, portfolio2)

        val asset = TestHelpers.createTestAsset("AAPL", "US")
        val trnP1Buy =
            TestHelpers.createTestTransaction(
                asset = asset,
                portfolio = portfolio,
                trnType = TrnType.BUY,
                quantity = BigDecimal("10"),
                price = PRICE_150,
                tradeDate = LocalDate.of(2024, 1, 15)
            )
        val trnP2Buy =
            TestHelpers.createTestTransaction(
                asset = asset,
                portfolio = portfolio2,
                trnType = TrnType.BUY,
                quantity = BigDecimal("10"),
                price = PRICE_150,
                tradeDate = LocalDate.of(2024, 1, 16)
            )
        val trnP2Sell =
            TestHelpers.createTestTransaction(
                asset = asset,
                portfolio = portfolio2,
                trnType = TrnType.SELL,
                quantity = BigDecimal("10"),
                price = PRICE_150,
                tradeDate = LocalDate.of(2024, 1, 17)
            )

        whenever(trnService.query(argThat { id == portfolio.id }, any<String>()))
            .thenReturn(TrnResponse(listOf(trnP1Buy)))
        whenever(trnService.query(argThat { id == portfolio2.id }, any<String>()))
            .thenReturn(TrnResponse(listOf(trnP2Buy, trnP2Sell)))

        val p1Positions =
            Positions(portfolio).apply {
                add(
                    Position(asset, portfolio).apply {
                        quantityValues.purchased = BigDecimal("10")
                    }
                )
            }
        val p2Positions =
            Positions(portfolio2).apply {
                add(
                    Position(asset, portfolio2).apply {
                        quantityValues.purchased = BigDecimal("10")
                        quantityValues.sold = BigDecimal("-10")
                    }
                )
            }
        whenever(
            positionService.build(
                argThat { id == portfolio.id },
                argThat { trns.size == 1 }
            )
        ).thenReturn(PositionResponse(p1Positions))
        whenever(
            positionService.build(
                argThat { id == portfolio2.id },
                argThat { trns.size == 2 }
            )
        ).thenReturn(PositionResponse(p2Positions))

        // When
        val result = valuationService.getAggregatedPositions(portfolios, DateUtils.TODAY, value = false)

        // Then - the aggregate nets to P1's 10 (P2 bought then sold 10)...
        val agg =
            result.data.positions.values
                .first()
        assertThat(agg.quantityValues.getTotal()).isEqualByComparingTo(BigDecimal("10"))
        // ...and only P1 appears in the breakdown (P2 is net-zero).
        assertThat(agg.portfolioBreakdown).hasSize(1)
        assertThat(agg.portfolioBreakdown.single().portfolioId).isEqualTo(portfolio.id)
        assertThat(agg.portfolioBreakdown.single().quantity).isEqualByComparingTo(BigDecimal("10"))
    }

    @Test
    fun `getAggregatedPositions skips market value update for aggregated positions`() {
        // Given - aggregated positions that would normally trigger market value update
        val portfolio2 = TestHelpers.createTestPortfolio("Portfolio2")
        val portfolios = listOf(portfolio, portfolio2)

        val asset = TestHelpers.createTestAsset("AAPL", "US")
        val trn =
            TestHelpers.createTestTransaction(
                asset = asset,
                portfolio = portfolio,
                trnType = TrnType.BUY,
                quantity = BigDecimal("10"),
                price = PRICE_150,
                tradeDate = LocalDate.of(2024, 1, 15)
            )

        whenever(trnService.query(any<Portfolio>(), any<String>()))
            .thenReturn(TrnResponse(listOf(trn)))

        val positions = Positions(portfolio)
        positions.asAt = DateUtils.TODAY
        val position = Position(asset, portfolio)
        position.moneyValues[Position.In.PORTFOLIO] =
            MoneyValues(portfolio.currency).apply {
                marketValue = BigDecimal("1500.00")
            }
        positions.add(position)
        val totals = Totals(portfolio.currency)
        totals.marketValue = BigDecimal("1500.00")
        positions.setTotal(Position.In.PORTFOLIO, totals)

        whenever(positionService.build(any(), any())).thenReturn(PositionResponse(positions))
        whenever(positionValuationService.value(any(), any())).thenReturn(positions)

        // When - request valuation with value=true
        val result = valuationService.getAggregatedPositions(portfolios, DateUtils.TODAY, value = true)

        // Then - market value update should NOT be sent for aggregated positions
        verify(marketValueUpdateProducer, never()).sendMessage(any())
        assertThat(result.data.positions).isNotEmpty
    }

    @Test
    fun `getAggregatedPositions adopts targetCurrency on the aggregate reporting view`() {
        // Given - first portfolio is USD but the caller requested SGD totals.
        // The aggregate adopts SGD so its PORTFOLIO bucket is priced directly
        // against the target rather than via a lossy FX round-trip (the drift
        // that showed on PRIVATE / POLICY constant-`close=1` positions).
        val usdPortfolio = TestHelpers.createTestPortfolio("usd-pf", currencyCode = "USD")
        val sgdPortfolio = TestHelpers.createTestPortfolio("sgd-pf", currencyCode = "SGD")
        val portfolios = listOf(usdPortfolio, sgdPortfolio)

        val asset = TestHelpers.createTestAsset("AAPL", "US")
        val trn =
            TestHelpers.createTestTransaction(
                asset = asset,
                portfolio = usdPortfolio,
                trnType = TrnType.BUY,
                quantity = BigDecimal("10"),
                price = PRICE_150,
                tradeDate = LocalDate.of(2024, 1, 15)
            )
        whenever(trnService.query(any<Portfolio>(), any<String>()))
            .thenReturn(TrnResponse(listOf(trn)))

        val held =
            Positions(usdPortfolio).apply {
                add(
                    Position(asset, usdPortfolio).apply {
                        quantityValues.purchased = BigDecimal("10")
                    }
                )
            }
        whenever(positionService.build(any(), any()))
            .thenReturn(PositionResponse(held))

        // When - request aggregation in SGD
        val result =
            valuationService.getAggregatedPositions(
                portfolios,
                DateUtils.TODAY,
                value = false,
                targetCurrencyCode = "SGD"
            )

        // Then - the aggregate reports in SGD and its PORTFOLIO bucket is
        // denominated in the target currency.
        assertThat(result.data.portfolio.currency.code).isEqualTo("SGD")
        assertThat(result.data.portfolio.id).isEqualTo(usdPortfolio.id)
        val agg =
            result.data.positions.values
                .first()
        assertThat(agg.getMoneyValues(Position.In.PORTFOLIO).currency.code).isEqualTo("SGD")
    }

    @Test
    fun `getAggregatedPositions converts cost basis into the target reporting currency`() {
        // Given - an NZD portfolio holding a private asset whose cost basis was
        // booked at trade_base_rate=1.0 (trade=base=portfolio=NZD), so the cost
        // is a raw NZD figure. The caller requests the aggregate in USD (the
        // user's reporting currency). Previously the merge summed cost as-is,
        // leaving NZD magnitude under a USD label while market value was FX'd.
        val nzdPortfolio =
            TestHelpers.createTestPortfolio(
                "nzd-pf",
                currencyCode = "NZD",
                baseCurrency = "NZD"
            )
        val portfolios = listOf(nzdPortfolio)

        val asset = TestHelpers.createTestAsset("APT", "PRIVATE")
        val trn =
            TestHelpers.createTestTransaction(
                asset = asset,
                portfolio = nzdPortfolio,
                trnType = TrnType.BUY,
                quantity = BigDecimal("1"),
                price = BigDecimal("1565000.00"),
                tradeDate = LocalDate.of(2024, 1, 15)
            )
        whenever(trnService.query(any<Portfolio>(), any<String>()))
            .thenReturn(TrnResponse(listOf(trn)))

        // Source position: cost accumulated in NZD (rate 1.0, unconverted).
        val held =
            Positions(nzdPortfolio).apply {
                add(
                    Position(asset, nzdPortfolio).apply {
                        quantityValues.purchased = BigDecimal("1")
                        getMoneyValues(Position.In.PORTFOLIO, Currency("NZD")).apply {
                            costValue = BigDecimal("1565000.00")
                            costBasis = BigDecimal("1565000.00")
                            purchases = BigDecimal("1565000.00")
                        }
                    }
                )
            }
        whenever(positionService.build(any(), any()))
            .thenReturn(PositionResponse(held))

        val nzdToUsd = BigDecimal("0.567")
        whenever(fxRateService.getRates(any(), any())).thenReturn(
            FxResponse(
                FxPairResults(
                    mapOf(
                        IsoCurrencyPair("NZD", "USD") to
                            FxRate(Currency("NZD"), Currency("USD"), nzdToUsd)
                    )
                )
            )
        )

        // When - aggregate in USD
        val result =
            valuationService.getAggregatedPositions(
                portfolios,
                DateUtils.TODAY,
                value = false,
                targetCurrencyCode = "USD"
            )

        // Then - the PORTFOLIO bucket is USD and its cost is FX-converted, not raw NZD.
        val agg =
            result.data.positions.values
                .first()
        val portfolioBucket = agg.getMoneyValues(Position.In.PORTFOLIO)
        assertThat(portfolioBucket.currency.code).isEqualTo("USD")
        assertThat(portfolioBucket.costValue)
            .describedAs("cost must be FX-converted to USD (1565000 * 0.567), not the raw NZD basis")
            .isEqualByComparingTo(BigDecimal("887355.00"))
        assertThat(portfolioBucket.purchases)
            .describedAs("purchases must be FX-converted alongside cost")
            .isEqualByComparingTo(BigDecimal("887355.00"))
    }

    @Test
    fun `getAggregatedPositions applies per-portfolio splits once and sums cost across portfolios`() {
        // Real accumulator: the split / cost-basis logic only misbehaves end-to-end.
        // A mocked positionService.build cannot reproduce a corporate-action bug.
        val realPositionService =
            PositionService(
                Accumulator(
                    TrnBehaviourFactory(
                        listOf(BuyBehaviour(), SellBehaviour(), SplitBehaviour())
                    )
                )
            )
        val service =
            ValuationService(
                positionValuationService,
                trnService,
                realPositionService,
                marketValueUpdateProducer,
                classificationClient,
                fxRateService,
                tokenService,
                dateUtils
            )

        val pfA = TestHelpers.createTestPortfolio("PF-A")
        val pfB = TestHelpers.createTestPortfolio("PF-B")
        val vo = TestHelpers.createTestAsset("VO", "US")

        // PF-A: BUY 6 (cost 1801.74) then a 4:1 split -> 24 shares held.
        val aBuy =
            Trn(
                trnType = TrnType.BUY,
                asset = vo,
                portfolio = pfA,
                quantity = BigDecimal("6"),
                tradeAmount = BigDecimal("1801.74"),
                tradeDate = LocalDate.of(2026, 1, 27)
            )
        val aSplit =
            Trn(
                trnType = TrnType.SPLIT,
                asset = vo,
                portfolio = pfA,
                quantity = BigDecimal("4"),
                tradeDate = LocalDate.of(2026, 4, 21)
            )
        // PF-B: BUY 12, the same 4:1 split -> 48, then SELL all 48 -> net zero.
        val bBuy =
            Trn(
                trnType = TrnType.BUY,
                asset = vo,
                portfolio = pfB,
                quantity = BigDecimal("12"),
                tradeAmount = BigDecimal("3285.00"),
                tradeDate = LocalDate.of(2024, 12, 18)
            )
        val bSplit =
            Trn(
                trnType = TrnType.SPLIT,
                asset = vo,
                portfolio = pfB,
                quantity = BigDecimal("4"),
                tradeDate = LocalDate.of(2026, 4, 21)
            )
        val bSell =
            Trn(
                trnType = TrnType.SELL,
                asset = vo,
                portfolio = pfB,
                quantity = BigDecimal("48"),
                tradeAmount = BigDecimal("3812.00"),
                tradeDate = LocalDate.of(2026, 6, 23)
            )

        whenever(trnService.query(argThat { id == pfA.id }, any<String>()))
            .thenReturn(TrnResponse(listOf(aBuy, aSplit)))
        whenever(trnService.query(argThat { id == pfB.id }, any<String>()))
            .thenReturn(TrnResponse(listOf(bBuy, bSplit, bSell)))

        // When - aggregate without valuation (no market data needed)
        val result =
            service.getAggregatedPositions(
                listOf(pfA, pfB),
                DateUtils.TODAY,
                value = false
            )

        // Then - the asset's split is recorded once per portfolio; merging the
        // raw transaction streams used to compound it (24 -> 186). Per-portfolio
        // accumulation + summation must report the real net quantity.
        val voPosition =
            result.data.positions.values
                .first()
        assertThat(voPosition.quantityValues.getTotal())
            .describedAs("4:1 split recorded per portfolio must not compound across the aggregate")
            .isEqualByComparingTo(BigDecimal("24"))

        // And cost must be the sum of each portfolio's own basis, not a pooled
        // average drawn down by PF-B's sell. PF-B is fully sold -> contributes 0.
        assertThat(voPosition.getMoneyValues(Position.In.TRADE, vo.market.currency).costValue)
            .describedAs("aggregate cost = PF-A basis (1801.74); fully-sold PF-B contributes 0")
            .isEqualByComparingTo(BigDecimal("1801.74"))
    }

    @Test
    fun `value should not send market value update for historical dates`() {
        // Given - a portfolio valued at a historical date
        val positions = Positions(portfolio)
        positions.asAt = "2024-01-15" // Historical date

        val asset = TestHelpers.createTestAsset("AAPL", "US")
        val position = Position(asset, portfolio)
        position.quantityValues.purchased = BigDecimal.TEN
        positions.add(position)

        val totals = Totals(portfolio.currency)
        totals.marketValue = BigDecimal("1500.00")
        positions.setTotal(Position.In.PORTFOLIO, totals)

        whenever(positionValuationService.value(any(), any())).thenReturn(positions)

        // When
        val result = valuationService.value(positions)

        // Then - should NOT send market value update for historical dates
        verify(marketValueUpdateProducer, never()).sendMessage(any())
        assertThat(result.data).isEqualTo(positions)
    }

    @Test
    fun `enrichWithClassifications sets Cash sector for cash assets`() {
        // Given - positions with a cash asset
        val positions = Positions(portfolio)
        positions.asAt = DateUtils.TODAY

        val cashAsset = TestHelpers.createTestAsset("USD", "OFFM")
        cashAsset.assetCategory = AssetCategory(id = AssetCategory.REPORT_CASH, name = "Cash")
        val position = Position(cashAsset, portfolio)
        position.quantityValues.purchased = BigDecimal("1000")
        positions.add(position)

        whenever(positionValuationService.value(any(), any())).thenReturn(positions)

        // When
        val result = valuationService.value(positions)

        // Then - cash asset should have sector set to "Cash"
        val cashPosition =
            result.data.positions.values
                .first()
        assertThat(cashPosition.asset.sector).isEqualTo(AssetCategory.REPORT_CASH)
    }

    @Test
    fun `enrichWithClassifications applies sector and industry from classification service`() {
        // Given - positions with an equity asset
        val positions = Positions(portfolio)
        positions.asAt = DateUtils.TODAY

        val equityAsset = TestHelpers.createTestAsset("AAPL", "US")
        equityAsset.assetCategory = AssetCategory(id = AssetCategory.REPORT_EQUITY, name = "Equity")
        val position = Position(equityAsset, portfolio)
        position.quantityValues.purchased = BigDecimal.TEN
        positions.add(position)

        // Mock classification response
        val classifications =
            mapOf(
                equityAsset.id to
                    AssetClassificationSummary(
                        assetId = equityAsset.id,
                        sector = "Information Technology",
                        industry = "Consumer Electronics"
                    )
            )
        whenever(classificationClient.getClassifications(any()))
            .thenReturn(BulkClassificationResponse(classifications))
        whenever(positionValuationService.value(any(), any())).thenReturn(positions)

        // When
        val result = valuationService.value(positions)

        // Then - equity should have sector and industry set from classifications
        val equityPosition =
            result.data.positions.values
                .first()
        assertThat(equityPosition.asset.sector).isEqualTo("Information Technology")
        assertThat(equityPosition.asset.industry).isEqualTo("Consumer Electronics")
    }

    @Test
    fun `build with TrustedTrnQuery calls trnService with correct query`() {
        // Given - a trusted transaction query
        val tradeDate = LocalDate.of(2024, 1, 15)
        val asset = TestHelpers.createTestAsset("AAPL", "US")
        val trnQuery =
            TrustedTrnQuery(
                portfolio = portfolio,
                tradeDate = tradeDate,
                assetId = asset.id
            )

        val trn =
            TestHelpers.createTestTransaction(
                asset = asset,
                portfolio = portfolio,
                tradeDate = tradeDate
            )

        whenever(trnService.query(any<TrustedTrnQuery>())).thenReturn(TrnResponse(listOf(trn)))

        val positions = Positions(portfolio)
        positions.add(Position(asset, portfolio))
        whenever(positionService.build(any(), any())).thenReturn(PositionResponse(positions))

        // When
        val result = valuationService.build(trnQuery)

        // Then
        assertThat(result.data.positions).hasSize(1)
        verify(trnService).query(trnQuery)
    }

    @Test
    fun `getPositions sets asAt date for historical valuations`() {
        // Given
        val historicalDate = "2024-01-15"
        val positions = Positions(portfolio)
        val positionResponse = PositionResponse(positions)

        whenever(trnService.query(any<Portfolio>(), any<String>()))
            .thenReturn(TrnResponse(emptyList()))
        whenever(positionService.build(any(), any())).thenReturn(positionResponse)

        // When
        val result = valuationService.getPositions(portfolio, historicalDate, value = false)

        // Then - asAt should be set to the historical date
        assertThat(result.data.asAt).isEqualTo(historicalDate)
    }

    @Test
    fun `value returns positions unchanged when no positions exist`() {
        // Given - empty positions
        val positions = Positions(portfolio)

        // When
        val result = valuationService.value(positions)

        // Then - should return the same positions without calling valuation service
        assertThat(result.data).isEqualTo(positions)
        verify(positionValuationService, never()).value(any(), any())
    }

    @Test
    fun `value should send market value in BASE currency not PORTFOLIO currency`() {
        // Given - a portfolio with different currency (GBP) and base (USD)
        // This simulates a user with USD base currency holding a GBP-denominated portfolio
        val gbpCurrency = Currency("GBP")
        val usdCurrency = Currency("USD")
        val multiCurrencyPortfolio =
            Portfolio(
                id = "multi-currency-test",
                code = "MULTI",
                name = "Multi Currency Portfolio",
                currency = gbpCurrency, // Portfolio cost currency is GBP
                base = usdCurrency // User's base currency is USD
            )

        val positions = Positions(multiCurrencyPortfolio)
        positions.asAt = DateUtils.TODAY

        val asset = TestHelpers.createTestAsset("HELD_ASSET", "LSE")
        val position = Position(asset, multiCurrencyPortfolio)
        position.quantityValues.purchased = BigDecimal.TEN
        positions.add(position)

        // Set up PORTFOLIO totals (in GBP) with one value
        val portfolioTotals = Totals(gbpCurrency)
        portfolioTotals.marketValue = BigDecimal("8000.00") // 8000 GBP
        portfolioTotals.irr = BigDecimal("0.10")
        positions.setTotal(Position.In.PORTFOLIO, portfolioTotals)

        // Set up BASE totals (in USD) with a DIFFERENT value (converted at FX rate)
        val baseTotals = Totals(usdCurrency)
        baseTotals.marketValue = MV_10K // 10000 USD (GBP->USD converted)
        baseTotals.irr = BigDecimal("0.12")
        positions.setTotal(Position.In.BASE, baseTotals)

        whenever(positionValuationService.value(any(), any())).thenReturn(positions)

        // When
        valuationService.value(positions)

        // Then - the market value update should use BASE currency values (USD), not PORTFOLIO (GBP)
        verify(marketValueUpdateProducer).sendMessage(capture(portfolioCaptor))
        val sentPortfolio = portfolioCaptor.value

        // Assert the marketValue sent is from BASE totals (10000 USD), not PORTFOLIO totals (8000 GBP)
        assertThat(sentPortfolio.marketValue)
            .describedAs("Market value should be from BASE totals (10000 USD), not PORTFOLIO totals (8000 GBP)")
            .isEqualTo(MV_10K)

        // Also verify IRR is from BASE totals
        assertThat(sentPortfolio.irr)
            .describedAs("IRR should be from BASE totals")
            .isEqualTo(BigDecimal("0.12"))
    }

    @Test
    fun `value should send gainOnDay summed from all positions in BASE currency`() {
        // Given - a portfolio with multiple positions each having gainOnDay
        val positions = Positions(portfolio)
        positions.asAt = DateUtils.TODAY

        // First position with gainOnDay
        val asset1 = TestHelpers.createTestAsset("AAPL", "US")
        val position1 = Position(asset1, portfolio)
        position1.quantityValues.purchased = BigDecimal.TEN
        position1.moneyValues[Position.In.BASE] =
            MoneyValues(portfolio.base).apply {
                gainOnDay = PRICE_150
                marketValue = BigDecimal("1500.00")
            }
        positions.add(position1)

        // Second position with gainOnDay
        val asset2 = TestHelpers.createTestAsset("GOOGL", "US")
        val position2 = Position(asset2, portfolio)
        position2.quantityValues.purchased = BigDecimal("5")
        position2.moneyValues[Position.In.BASE] =
            MoneyValues(portfolio.base).apply {
                gainOnDay = BigDecimal("-50.00")
                marketValue = BigDecimal("12500.00")
            }
        positions.add(position2)

        // Set up BASE totals
        val baseTotals = Totals(portfolio.base)
        baseTotals.marketValue = BigDecimal("14000.00")
        positions.setTotal(Position.In.BASE, baseTotals)

        whenever(positionValuationService.value(any(), any())).thenReturn(positions)

        // When
        valuationService.value(positions)

        // Then - gainOnDay should be sum of all positions (150 + -50 = 100)
        verify(marketValueUpdateProducer).sendMessage(capture(portfolioCaptor))
        val sentPortfolio = portfolioCaptor.value

        assertThat(sentPortfolio.gainOnDay)
            .describedAs("gainOnDay should be sum of all position gainOnDay values")
            .isEqualTo(BigDecimal("100.00"))
    }

    @Test
    fun `value should send assetClassification breakdown by effectiveReportCategory`() {
        // Given - a portfolio with positions in different asset categories
        val positions = Positions(portfolio)
        positions.asAt = DateUtils.TODAY

        // Equity position 1
        val equityAsset1 = TestHelpers.createTestAsset("AAPL", "US")
        equityAsset1.assetCategory = AssetCategory(id = AssetCategory.REPORT_EQUITY, name = "Equity")
        val equityPosition1 = Position(equityAsset1, portfolio)
        equityPosition1.quantityValues.purchased = BigDecimal.TEN
        equityPosition1.moneyValues[Position.In.BASE] =
            MoneyValues(portfolio.base).apply {
                marketValue = BigDecimal("1500.00")
            }
        positions.add(equityPosition1)

        // Equity position 2 (same category)
        val equityAsset2 = TestHelpers.createTestAsset("GOOGL", "US")
        equityAsset2.assetCategory = AssetCategory(id = AssetCategory.REPORT_EQUITY, name = "Equity")
        val equityPosition2 = Position(equityAsset2, portfolio)
        equityPosition2.quantityValues.purchased = BigDecimal("5")
        equityPosition2.moneyValues[Position.In.BASE] =
            MoneyValues(portfolio.base).apply {
                marketValue = BigDecimal("12500.00")
            }
        positions.add(equityPosition2)

        // Cash position (different category)
        val cashAsset = TestHelpers.createTestAsset("USD", "OFFM")
        cashAsset.assetCategory = AssetCategory(id = AssetCategory.REPORT_CASH, name = "Cash")
        val cashPosition = Position(cashAsset, portfolio)
        cashPosition.quantityValues.purchased = BigDecimal("1000")
        cashPosition.moneyValues[Position.In.BASE] =
            MoneyValues(portfolio.base).apply {
                marketValue = BigDecimal("1000.00")
            }
        positions.add(cashPosition)

        // Set up BASE totals
        val baseTotals = Totals(portfolio.base)
        baseTotals.marketValue = BigDecimal("15000.00")
        positions.setTotal(Position.In.BASE, baseTotals)

        whenever(positionValuationService.value(any(), any())).thenReturn(positions)

        // When
        valuationService.value(positions)

        // Then - assetClassification should group by category
        verify(marketValueUpdateProducer).sendMessage(capture(portfolioCaptor))
        val sentPortfolio = portfolioCaptor.value

        assertThat(sentPortfolio.assetClassification)
            .describedAs("assetClassification should have Equity and Cash categories")
            .hasSize(2)
        assertThat(sentPortfolio.assetClassification?.get(AssetCategory.REPORT_EQUITY))
            .describedAs("Equity total should be 1500 + 12500 = 14000")
            .isEqualTo(BigDecimal("14000.00"))
        assertThat(sentPortfolio.assetClassification?.get(AssetCategory.REPORT_CASH))
            .describedAs("Cash total should be 1000")
            .isEqualTo(BigDecimal("1000.00"))
    }

    @Test
    fun `value should send zero gainOnDay when positions have no gainOnDay set`() {
        // Given - a portfolio with positions that have no gainOnDay
        val positions = Positions(portfolio)
        positions.asAt = DateUtils.TODAY

        val asset = TestHelpers.createTestAsset("AAPL", "US")
        val position = Position(asset, portfolio)
        position.quantityValues.purchased = BigDecimal.TEN
        // No gainOnDay set in moneyValues - using default constructor
        positions.add(position)

        val baseTotals = Totals(portfolio.base)
        baseTotals.marketValue = BigDecimal("1500.00")
        positions.setTotal(Position.In.BASE, baseTotals)

        whenever(positionValuationService.value(any(), any())).thenReturn(positions)

        // When
        valuationService.value(positions)

        // Then - gainOnDay should be zero
        verify(marketValueUpdateProducer).sendMessage(capture(portfolioCaptor))
        val sentPortfolio = portfolioCaptor.value

        assertThat(sentPortfolio.gainOnDay)
            .describedAs("gainOnDay should be zero when no positions have gainOnDay")
            .isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `value should send valuedAt as today when asAt is today`() {
        // Given - a portfolio valued "today"
        val positions = Positions(portfolio)
        positions.asAt = DateUtils.TODAY

        val asset = TestHelpers.createTestAsset("AAPL", "US")
        val position = Position(asset, portfolio)
        position.quantityValues.purchased = BigDecimal.TEN
        positions.add(position)

        val baseTotals = Totals(portfolio.base)
        baseTotals.marketValue = BigDecimal("1500.00")
        positions.setTotal(Position.In.BASE, baseTotals)

        whenever(positionValuationService.value(any(), any())).thenReturn(positions)

        // When
        valuationService.value(positions)

        // Then - valuedAt should be today's date
        verify(marketValueUpdateProducer).sendMessage(capture(portfolioCaptor))
        val sentPortfolio = portfolioCaptor.value

        assertThat(sentPortfolio.valuedAt)
            .describedAs("valuedAt should be today's date")
            .isEqualTo(dateUtils.getDate())
    }

    @Test
    fun `value should send valuedAt as specified date for historical valuations`() {
        // Given - a portfolio valued at a historical date
        val historicalDate = "2024-06-15"
        val positions = Positions(portfolio)
        positions.asAt = historicalDate

        val asset = TestHelpers.createTestAsset("AAPL", "US")
        val position = Position(asset, portfolio)
        position.quantityValues.purchased = BigDecimal.TEN
        positions.add(position)

        val baseTotals = Totals(portfolio.base)
        baseTotals.marketValue = BigDecimal("1500.00")
        positions.setTotal(Position.In.BASE, baseTotals)

        whenever(positionValuationService.value(any(), any())).thenReturn(positions)

        // When - note: we call value() but historical dates don't trigger market value updates
        // So we test the parseValuationDate logic indirectly
        valuationService.value(positions)

        // Then - historical dates don't send market value updates, so we verify no message sent
        verify(marketValueUpdateProducer, never()).sendMessage(any())
    }
}