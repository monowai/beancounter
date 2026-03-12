package com.beancounter.marketdata.trn

import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.PositionMoveRequest
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.Constants.Companion.usdCashBalance
import com.beancounter.marketdata.cache.CacheInvalidationProducer
import com.beancounter.marketdata.portfolio.PortfolioService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Unit tests for PositionMoveService - moves all transactions
 * for an asset from one portfolio to another, optionally creating
 * compensating cash transactions to maintain cash balances.
 */
class PositionMoveServiceTest {
    private lateinit var trnRepository: TrnRepository
    private lateinit var portfolioService: PortfolioService
    private lateinit var fxTransactions: FxTransactions
    private lateinit var trnService: TrnService
    private lateinit var cacheInvalidationProducer: CacheInvalidationProducer
    private lateinit var positionMoveService: PositionMoveService

    private val owner = SystemUser("test-user", "test@example.com")

    private val sourcePortfolio =
        Portfolio(
            id = "source-portfolio-id",
            code = "SOURCE",
            name = "Source Portfolio",
            currency = USD,
            base = USD,
            owner = owner
        )

    private val targetPortfolio =
        Portfolio(
            id = "target-portfolio-id",
            code = "TARGET",
            name = "Target Portfolio",
            currency = USD,
            base = USD,
            owner = owner
        )

    @BeforeEach
    fun setUp() {
        trnRepository = mock()
        portfolioService = mock()
        fxTransactions = mock()
        trnService = mock()
        cacheInvalidationProducer = mock()
        positionMoveService =
            PositionMoveService(
                trnRepository,
                portfolioService,
                fxTransactions,
                trnService,
                cacheInvalidationProducer
            )

        whenever(portfolioService.find("source-portfolio-id")).thenReturn(sourcePortfolio)
        whenever(portfolioService.find("target-portfolio-id")).thenReturn(targetPortfolio)
    }

    @Test
    fun `should move transactions to target portfolio`() {
        val trn = createBuyTransaction("trn-1", sourcePortfolio)
        whenever(trnRepository.findByPortfolioIdAndAssetId(sourcePortfolio.id, MSFT.id))
            .thenReturn(listOf(trn))

        val result =
            positionMoveService.movePosition(
                PositionMoveRequest(
                    sourcePortfolioId = sourcePortfolio.id,
                    targetPortfolioId = targetPortfolio.id,
                    assetId = MSFT.id
                )
            )

        assertThat(result.movedCount).isEqualTo(1)
        assertThat(result.compensatingTransactions).isEqualTo(0)
        assertThat(trn.portfolio).isEqualTo(targetPortfolio)
        verify(trnRepository).saveAll(any<List<Trn>>())
    }

    @Test
    fun `should throw when no transactions found`() {
        whenever(trnRepository.findByPortfolioIdAndAssetId(sourcePortfolio.id, MSFT.id))
            .thenReturn(emptyList())

        assertThatThrownBy {
            positionMoveService.movePosition(
                PositionMoveRequest(
                    sourcePortfolioId = sourcePortfolio.id,
                    targetPortfolioId = targetPortfolio.id,
                    assetId = MSFT.id
                )
            )
        }.isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `should create consolidated compensating cash transactions`() {
        val buyTrn = createBuyTransaction("trn-1", sourcePortfolio)
        whenever(trnRepository.findByPortfolioIdAndAssetId(sourcePortfolio.id, MSFT.id))
            .thenReturn(listOf(buyTrn))

        val result =
            positionMoveService.movePosition(
                PositionMoveRequest(
                    sourcePortfolioId = sourcePortfolio.id,
                    targetPortfolioId = targetPortfolio.id,
                    assetId = MSFT.id,
                    maintainCashBalances = true
                )
            )

        assertThat(result.movedCount).isEqualTo(1)
        assertThat(result.compensatingTransactions).isEqualTo(2)

        val requestCaptor = argumentCaptor<TrnRequest>()
        verify(trnService).save(eq(sourcePortfolio), requestCaptor.capture())
        verify(trnService).save(eq(targetPortfolio), requestCaptor.capture())

        // BUY debits cash (negative cashAmount), so source gets DEPOSIT to reverse
        val sourceRequest = requestCaptor.firstValue
        assertThat(sourceRequest.data).hasSize(1)
        assertThat(sourceRequest.data[0].trnType).isEqualTo(TrnType.DEPOSIT)
        assertThat(sourceRequest.data[0].tradeAmount).isEqualByComparingTo(BigDecimal("5000"))

        // Target gets WITHDRAWAL to replicate the debit
        val targetRequest = requestCaptor.secondValue
        assertThat(targetRequest.data).hasSize(1)
        assertThat(targetRequest.data[0].trnType).isEqualTo(TrnType.WITHDRAWAL)
        assertThat(targetRequest.data[0].tradeAmount).isEqualByComparingTo(BigDecimal("5000"))
    }

    @Test
    fun `should consolidate multiple transactions to net amount per cash asset`() {
        // 2 BUYs ($5000 each = -$10000 cash) and 1 SELL ($3000 = +$3000 cash)
        // Net = -$7000, so source gets DEPOSIT $7000, target gets WITHDRAWAL $7000
        val buy1 = createBuyTransaction("trn-1", sourcePortfolio)
        val buy2 = createBuyTransaction("trn-2", sourcePortfolio)
        val sell = createSellTransaction("trn-3", sourcePortfolio)
        whenever(trnRepository.findByPortfolioIdAndAssetId(sourcePortfolio.id, MSFT.id))
            .thenReturn(listOf(buy1, buy2, sell))

        val result =
            positionMoveService.movePosition(
                PositionMoveRequest(
                    sourcePortfolioId = sourcePortfolio.id,
                    targetPortfolioId = targetPortfolio.id,
                    assetId = MSFT.id,
                    maintainCashBalances = true
                )
            )

        assertThat(result.movedCount).isEqualTo(3)
        // Only 1 consolidated pair (not 3 pairs)
        assertThat(result.compensatingTransactions).isEqualTo(2)

        val requestCaptor = argumentCaptor<TrnRequest>()
        verify(trnService).save(eq(sourcePortfolio), requestCaptor.capture())
        verify(trnService).save(eq(targetPortfolio), requestCaptor.capture())

        // Net is -$7000, so source gets single DEPOSIT
        val sourceRequest = requestCaptor.firstValue
        assertThat(sourceRequest.data).hasSize(1)
        assertThat(sourceRequest.data[0].trnType).isEqualTo(TrnType.DEPOSIT)
        assertThat(sourceRequest.data[0].tradeAmount).isEqualByComparingTo(BigDecimal("7000"))

        // Target gets single WITHDRAWAL
        val targetRequest = requestCaptor.secondValue
        assertThat(targetRequest.data).hasSize(1)
        assertThat(targetRequest.data[0].trnType).isEqualTo(TrnType.WITHDRAWAL)
        assertThat(targetRequest.data[0].tradeAmount).isEqualByComparingTo(BigDecimal("7000"))
    }

    @Test
    fun `should create reverse compensating transactions for SELL`() {
        val sellTrn = createSellTransaction("trn-1", sourcePortfolio)
        whenever(trnRepository.findByPortfolioIdAndAssetId(sourcePortfolio.id, MSFT.id))
            .thenReturn(listOf(sellTrn))

        val result =
            positionMoveService.movePosition(
                PositionMoveRequest(
                    sourcePortfolioId = sourcePortfolio.id,
                    targetPortfolioId = targetPortfolio.id,
                    assetId = MSFT.id,
                    maintainCashBalances = true
                )
            )

        assertThat(result.compensatingTransactions).isEqualTo(2)

        val requestCaptor = argumentCaptor<TrnRequest>()
        verify(trnService).save(eq(sourcePortfolio), requestCaptor.capture())
        verify(trnService).save(eq(targetPortfolio), requestCaptor.capture())

        // SELL credits cash (positive cashAmount), so source gets WITHDRAWAL to reverse
        val sourceRequest = requestCaptor.firstValue
        assertThat(sourceRequest.data[0].trnType).isEqualTo(TrnType.WITHDRAWAL)

        // Target gets DEPOSIT to replicate the credit
        val targetRequest = requestCaptor.secondValue
        assertThat(targetRequest.data[0].trnType).isEqualTo(TrnType.DEPOSIT)
    }

    @Test
    fun `should skip non-cash-impacted transactions for compensating`() {
        val splitTrn =
            Trn(
                id = "trn-split",
                trnType = TrnType.SPLIT,
                asset = MSFT,
                quantity = BigDecimal("2"),
                tradeCurrency = USD,
                portfolio = sourcePortfolio,
                tradeDate = LocalDate.now().minusDays(5),
                status = TrnStatus.SETTLED
            )
        whenever(trnRepository.findByPortfolioIdAndAssetId(sourcePortfolio.id, MSFT.id))
            .thenReturn(listOf(splitTrn))

        val result =
            positionMoveService.movePosition(
                PositionMoveRequest(
                    sourcePortfolioId = sourcePortfolio.id,
                    targetPortfolioId = targetPortfolio.id,
                    assetId = MSFT.id,
                    maintainCashBalances = true
                )
            )

        assertThat(result.movedCount).isEqualTo(1)
        assertThat(result.compensatingTransactions).isEqualTo(0)
        verify(trnService, never()).save(any<Portfolio>(), any<TrnRequest>())
    }

    @Test
    fun `should skip compensating when buy and sell net to zero`() {
        // BUY -$5000 + SELL +$5000 = net $0 — no compensating transactions needed
        val buy = createBuyTransaction("trn-1", sourcePortfolio)
        val sell =
            Trn(
                id = "trn-2",
                trnType = TrnType.SELL,
                asset = MSFT,
                quantity = BigDecimal("100"),
                tradeAmount = BigDecimal("5000"),
                tradeCurrency = USD,
                cashAsset = usdCashBalance,
                cashCurrency = USD,
                cashAmount = BigDecimal("5000"),
                portfolio = sourcePortfolio,
                tradeDate = LocalDate.now().minusDays(3),
                status = TrnStatus.SETTLED
            )
        whenever(trnRepository.findByPortfolioIdAndAssetId(sourcePortfolio.id, MSFT.id))
            .thenReturn(listOf(buy, sell))

        val result =
            positionMoveService.movePosition(
                PositionMoveRequest(
                    sourcePortfolioId = sourcePortfolio.id,
                    targetPortfolioId = targetPortfolio.id,
                    assetId = MSFT.id,
                    maintainCashBalances = true
                )
            )

        assertThat(result.movedCount).isEqualTo(2)
        assertThat(result.compensatingTransactions).isEqualTo(0)
        verify(trnService, never()).save(any<Portfolio>(), any<TrnRequest>())
    }

    @Test
    fun `should recalculate FX rates when portfolio currencies differ`() {
        val nzdTargetPortfolio =
            Portfolio(
                id = "nzd-portfolio-id",
                code = "NZD-TARGET",
                name = "NZD Target",
                currency = NZD,
                base = NZD,
                owner = owner
            )
        whenever(portfolioService.find("nzd-portfolio-id")).thenReturn(nzdTargetPortfolio)

        val trn = createBuyTransaction("trn-1", sourcePortfolio)
        whenever(trnRepository.findByPortfolioIdAndAssetId(sourcePortfolio.id, MSFT.id))
            .thenReturn(listOf(trn))

        positionMoveService.movePosition(
            PositionMoveRequest(
                sourcePortfolioId = sourcePortfolio.id,
                targetPortfolioId = nzdTargetPortfolio.id,
                assetId = MSFT.id
            )
        )

        verify(fxTransactions).setRates(eq(nzdTargetPortfolio), any())
        assertThat(trn.portfolio).isEqualTo(nzdTargetPortfolio)
    }

    @Test
    fun `should not recalculate FX rates when portfolio currencies match`() {
        val trn = createBuyTransaction("trn-1", sourcePortfolio)
        whenever(trnRepository.findByPortfolioIdAndAssetId(sourcePortfolio.id, MSFT.id))
            .thenReturn(listOf(trn))

        positionMoveService.movePosition(
            PositionMoveRequest(
                sourcePortfolioId = sourcePortfolio.id,
                targetPortfolioId = targetPortfolio.id,
                assetId = MSFT.id
            )
        )

        verify(fxTransactions, never()).setRates(any(), any())
    }

    @Test
    fun `should invalidate caches for both portfolios`() {
        val tradeDate = LocalDate.now().minusDays(30)
        val trn =
            Trn(
                id = "trn-1",
                trnType = TrnType.BUY,
                asset = MSFT,
                quantity = BigDecimal("100"),
                tradeAmount = BigDecimal("5000"),
                tradeCurrency = USD,
                cashAsset = usdCashBalance,
                cashCurrency = USD,
                cashAmount = BigDecimal("-5000"),
                portfolio = sourcePortfolio,
                tradeDate = tradeDate,
                status = TrnStatus.SETTLED
            )
        whenever(trnRepository.findByPortfolioIdAndAssetId(sourcePortfolio.id, MSFT.id))
            .thenReturn(listOf(trn))

        positionMoveService.movePosition(
            PositionMoveRequest(
                sourcePortfolioId = sourcePortfolio.id,
                targetPortfolioId = targetPortfolio.id,
                assetId = MSFT.id
            )
        )

        verify(cacheInvalidationProducer).sendTransactionEvent(sourcePortfolio.id, tradeDate)
        verify(cacheInvalidationProducer).sendTransactionEvent(targetPortfolio.id, tradeDate)
    }

    @Test
    fun `should move multiple transactions`() {
        val trn1 = createBuyTransaction("trn-1", sourcePortfolio)
        val trn2 = createBuyTransaction("trn-2", sourcePortfolio)
        val trn3 = createSellTransaction("trn-3", sourcePortfolio)
        whenever(trnRepository.findByPortfolioIdAndAssetId(sourcePortfolio.id, MSFT.id))
            .thenReturn(listOf(trn1, trn2, trn3))

        val result =
            positionMoveService.movePosition(
                PositionMoveRequest(
                    sourcePortfolioId = sourcePortfolio.id,
                    targetPortfolioId = targetPortfolio.id,
                    assetId = MSFT.id
                )
            )

        assertThat(result.movedCount).isEqualTo(3)
        assertThat(trn1.portfolio).isEqualTo(targetPortfolio)
        assertThat(trn2.portfolio).isEqualTo(targetPortfolio)
        assertThat(trn3.portfolio).isEqualTo(targetPortfolio)
    }

    private fun createBuyTransaction(
        id: String,
        portfolio: Portfolio
    ): Trn =
        Trn(
            id = id,
            trnType = TrnType.BUY,
            asset = MSFT,
            quantity = BigDecimal("100"),
            tradeAmount = BigDecimal("5000"),
            tradeCurrency = USD,
            cashAsset = usdCashBalance,
            cashCurrency = USD,
            cashAmount = BigDecimal("-5000"),
            portfolio = portfolio,
            tradeDate = LocalDate.now().minusDays(10),
            status = TrnStatus.SETTLED
        )

    private fun createSellTransaction(
        id: String,
        portfolio: Portfolio
    ): Trn =
        Trn(
            id = id,
            trnType = TrnType.SELL,
            asset = MSFT,
            quantity = BigDecimal("50"),
            tradeAmount = BigDecimal("3000"),
            tradeCurrency = USD,
            cashAsset = usdCashBalance,
            cashCurrency = USD,
            cashAmount = BigDecimal("3000"),
            portfolio = portfolio,
            tradeDate = LocalDate.now().minusDays(5),
            status = TrnStatus.SETTLED
        )
}