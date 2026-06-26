package com.beancounter.marketdata.trn

import com.beancounter.common.contracts.PositionMoveRequest
import com.beancounter.common.contracts.PositionMoveResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.portfolio.PortfolioService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for PortfolioMergeService — consolidates every transaction in the
 * source portfolio into the target (reusing the per-asset PositionMoveService)
 * then deletes the emptied source. Drives the "downgrade to a single portfolio"
 * (zen) wizard.
 */
class PortfolioMergeServiceTest {
    private lateinit var trnRepository: TrnRepository
    private lateinit var portfolioService: PortfolioService
    private lateinit var positionMoveService: PositionMoveService
    private lateinit var portfolioMergeService: PortfolioMergeService

    private val owner = SystemUser("test-user", "test@example.com")

    private val source =
        Portfolio(
            id = "source-id",
            code = "SOURCE",
            name = "Source",
            currency = USD,
            base = USD,
            owner = owner
        )

    private val target =
        Portfolio(
            id = "target-id",
            code = "TARGET",
            name = "Target",
            currency = USD,
            base = USD,
            owner = owner
        )

    @BeforeEach
    fun setUp() {
        trnRepository = mock()
        portfolioService = mock()
        positionMoveService = mock()
        portfolioMergeService =
            PortfolioMergeService(
                trnRepository,
                portfolioService,
                positionMoveService
            )
        whenever(portfolioService.find("source-id")).thenReturn(source)
        whenever(portfolioService.find("target-id")).thenReturn(target)
    }

    @Test
    fun `moves every source asset to the target then deletes the source`() {
        whenever(trnRepository.findDistinctAssetIdsByPortfolioIds(listOf(source.id)))
            .thenReturn(listOf(MSFT.id, "apple-id"))
        whenever(positionMoveService.movePosition(any()))
            .thenReturn(PositionMoveResponse(movedCount = 3))

        val result = portfolioMergeService.merge(source.id, target.id)

        // One move call per distinct asset, never compensating cash (carry verbatim).
        verify(positionMoveService).movePosition(
            PositionMoveRequest(source.id, target.id, MSFT.id, maintainCashBalances = false)
        )
        verify(positionMoveService).movePosition(
            PositionMoveRequest(source.id, target.id, "apple-id", maintainCashBalances = false)
        )
        verify(portfolioService).delete(source.id)

        assertThat(result.assetsMoved).isEqualTo(2)
        assertThat(result.transactionsMoved).isEqualTo(6)
        assertThat(result.sourceDeleted).isTrue()
        assertThat(result.targetPortfolioId).isEqualTo(target.id)
    }

    @Test
    fun `deletes an already-empty source with nothing to move`() {
        whenever(trnRepository.findDistinctAssetIdsByPortfolioIds(listOf(source.id)))
            .thenReturn(emptyList())

        val result = portfolioMergeService.merge(source.id, target.id)

        verify(positionMoveService, never()).movePosition(any())
        verify(portfolioService).delete(source.id)
        assertThat(result.assetsMoved).isEqualTo(0)
        assertThat(result.transactionsMoved).isEqualTo(0)
        assertThat(result.sourceDeleted).isTrue()
    }

    @Test
    fun `refuses to merge a portfolio into itself`() {
        assertThatThrownBy { portfolioMergeService.merge(source.id, source.id) }
            .isInstanceOf(BusinessException::class.java)

        verify(positionMoveService, never()).movePosition(any())
        verify(portfolioService, never()).delete(eq(source.id))
    }

    @Test
    fun `resolves both portfolios so ownership is enforced before moving`() {
        whenever(trnRepository.findDistinctAssetIdsByPortfolioIds(listOf(source.id)))
            .thenReturn(emptyList())

        portfolioMergeService.merge(source.id, target.id)

        verify(portfolioService).find(source.id)
        verify(portfolioService).find(target.id)
    }
}