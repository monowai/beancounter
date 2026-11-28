package com.beancounter.event.distribution

import com.beancounter.auth.TokenService
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.client.services.TrnService
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.QuantityValues
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.event.behaviour.EventBehaviourFactory
import com.beancounter.event.behaviour.TaxService
import com.beancounter.event.behaviour.alpha.AlphaEventAdapter
import com.beancounter.event.corporate.CorporateEventStore
import com.beancounter.event.corporate.EventRepository
import com.beancounter.event.metrics.EventMetrics
import com.beancounter.event.utils.TestHelpers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.math.BigDecimal
import java.time.LocalDate

/**
 * An imported holding (an ADD with quantity Q, cost date C and trade date T) is owed a dividend
 * recorded on R when C <= R < T, for Q divided by the splits after R up to T. Splits and every
 * other event never use this path, and an ADD without a cost date is owed nothing through it.
 */
@ExtendWith(MockitoExtension::class)
class CostDatedHoldingsTest {
    @Mock
    private lateinit var trnService: TrnService

    @Mock
    private lateinit var eventRepository: EventRepository

    @Mock
    private lateinit var keyGenUtils: KeyGenUtils

    @Mock
    private lateinit var positionGateway: PositionGateway

    @Mock
    private lateinit var portfolioService: PortfolioServiceClient

    @Mock
    private lateinit var tokenService: TokenService

    @Mock
    private lateinit var eventPublisher: EventPublisher

    @Mock
    private lateinit var eventMetrics: EventMetrics

    private lateinit var eventDistribution: EventDistribution

    private val asset = TestHelpers.createTestAsset("IMPD")
    private val importer = TestHelpers.createTestPortfolio("importer")
    private val holder = TestHelpers.createTestPortfolio("holder")
    private val costDate = LocalDate.of(2016, 1, 4)
    private val tradeDate = LocalDate.of(2026, 11, 18)
    private val rate = BigDecimal("0.50")

    @BeforeEach
    fun setUp() {
        val eventStore = CorporateEventStore(eventRepository, keyGenUtils)
        val positionService = PositionService(EventBehaviourFactory(AlphaEventAdapter(TaxService())))
        positionService.setPositionGateway(positionGateway)
        positionService.setPortfolioClientService(portfolioService)
        positionService.setTokenService(tokenService)
        eventDistribution =
            EventDistribution(
                positionService,
                eventStore,
                eventMetrics,
                eventPublisher,
                CostDatedHoldings(trnService, eventStore)
            )
        lenient().`when`(tokenService.bearerToken).thenReturn("token")
        lenient().`when`(portfolioService.getWhereHeld(any(), any())).thenReturn(PortfoliosResponse(emptyList()))
        lenient().`when`(positionGateway.query(any(), any())).thenAnswer {
            PositionResponse(Positions(it.getArgument<TrustedTrnQuery>(1).portfolio))
        }
        lenient()
            .`when`(eventRepository.findByAssetIdAndRecordDateBetweenOrderByRecordDateAsc(any(), any(), any()))
            .thenReturn(emptyList())
    }

    private fun imported(costDate: LocalDate? = this.costDate) {
        val add =
            Trn(
                trnType = TrnType.ADD,
                asset = asset,
                portfolio = importer,
                quantity = BigDecimal.TEN,
                tradeDate = tradeDate,
                costDate = costDate
            )
        lenient().`when`(trnService.queryCostDated(any(), any())).thenReturn(TrnResponse(listOf(add)))
    }

    private fun splits(vararg splits: CorporateEvent) {
        lenient()
            .`when`(eventRepository.findByAssetIdAndRecordDateBetweenOrderByRecordDateAsc(any(), any(), any()))
            .thenAnswer { invocation ->
                val start = invocation.getArgument<LocalDate>(1)
                val end = invocation.getArgument<LocalDate>(2)
                splits.filter { it.recordDate in start..end }
            }
    }

    private fun event(
        recordDate: LocalDate,
        trnType: TrnType = TrnType.DIVI
    ) = CorporateEvent(
        id = "event-$recordDate",
        trnType = trnType,
        assetId = asset.id,
        recordDate = recordDate,
        rate = if (trnType == TrnType.DIVI) rate else BigDecimal.ZERO,
        split = if (trnType == TrnType.SPLIT) BigDecimal("2") else BigDecimal.ONE
    )

    private fun owed(event: CorporateEvent): Map<String, TrustedTrnEvent> =
        eventDistribution.processEvent(event).associateBy { it.portfolio.id }

    @Test
    fun `should owe an imported holding a dividend recorded between its cost and trade dates`() {
        imported()

        val owed = owed(event(LocalDate.of(2019, 5, 10)))

        val dividend = owed.getValue(importer.id).trnInput
        assertThat(owed).containsOnlyKeys(importer.id)
        assertThat(dividend.trnType).isEqualTo(TrnType.DIVI)
        assertThat(dividend.quantity).isEqualByComparingTo("10")
        assertThat(dividend.status).isEqualTo(TrnStatus.PROPOSED)
        assertThat(dividend.tradeDate).isEqualTo(LocalDate.of(2019, 5, 20))
        assertThat(dividend.tax).isEqualByComparingTo("1.50")
        assertThat(dividend.tradeAmount).isEqualByComparingTo("3.50")
    }

    @Test
    fun `should owe the pre-split quantity when a split falls between the record and trade dates`() {
        imported()
        splits(event(LocalDate.of(2020, 8, 31), TrnType.SPLIT), event(LocalDate.of(2017, 3, 1), TrnType.SPLIT))

        val owed = owed(event(LocalDate.of(2018, 5, 10)))

        assertThat(owed.getValue(importer.id).trnInput.quantity).isEqualByComparingTo("5")
    }

    @Test
    fun `should not owe a dividend recorded before the cost date`() {
        imported()

        assertThat(owed(event(costDate.minusDays(1)))).isEmpty()
    }

    @Test
    fun `should not owe a dividend recorded on or after the trade date through this path`() {
        imported()

        assertThat(owed(event(tradeDate))).isEmpty()
        assertThat(owed(event(tradeDate.plusDays(3)))).isEmpty()
    }

    @Test
    fun `should generate nothing for a split recorded between the cost and trade dates`() {
        imported()

        assertThat(owed(event(LocalDate.of(2020, 8, 31), TrnType.SPLIT))).isEmpty()
        verify(trnService, never()).queryCostDated(any(), any())
    }

    @Test
    fun `should owe nothing through this path to an ADD without a cost date`() {
        imported(costDate = null)

        assertThat(owed(event(LocalDate.of(2019, 5, 10)))).isEmpty()
    }

    @Test
    fun `should add the imported shares to what the holding held on the record date`() {
        imported()
        lenient().`when`(portfolioService.getWhereHeld(any(), any())).thenReturn(PortfoliosResponse(listOf(importer)))
        lenient().`when`(positionGateway.query(any(), any())).thenReturn(PositionResponse(held(importer, "3")))

        val owed = owed(event(LocalDate.of(2019, 5, 10)))

        assertThat(owed.getValue(importer.id).trnInput.quantity).isEqualByComparingTo("13")
    }

    @Test
    fun `should still pay the holders when the imported holdings cannot be read`() {
        lenient().`when`(trnService.queryCostDated(any(), any())).thenThrow(BusinessException("down"))
        lenient().`when`(portfolioService.getWhereHeld(any(), any())).thenReturn(PortfoliosResponse(listOf(holder)))
        lenient().`when`(positionGateway.query(any(), any())).thenReturn(PositionResponse(held(holder, "4")))

        val owed = owed(event(LocalDate.of(2019, 5, 10)))

        assertThat(owed).containsOnlyKeys(holder.id)
        assertThat(owed.getValue(holder.id).trnInput.quantity).isEqualByComparingTo("4")
    }

    private fun held(
        portfolio: Portfolio,
        quantity: String
    ): Positions {
        val positions = Positions(portfolio)
        val position = Position(asset, portfolio)
        position.quantityValues = QuantityValues(purchased = BigDecimal(quantity))
        positions.add(position)
        return positions
    }
}