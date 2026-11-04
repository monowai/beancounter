package com.beancounter.marketdata.trn

import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.trn.settlement.TrnSettlementService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Read-only finders for transactions scoped to a portfolio, status, model or cash
 * asset. Finders across the current user's portfolios live in [UserTrnFinder];
 * mutations and lifecycle live in [TrnService] and [TrnSettlementService];
 * asset/event queries live in [TrnQueryService].
 */
@Service
@Transactional
class TrnFinder(
    private val trnRepository: TrnRepository,
    private val portfolioService: PortfolioService,
    private val dateUtils: DateUtils,
    private val trnPostProcessor: TrnPostProcessor
) {
    private val log = LoggerFactory.getLogger(TrnFinder::class.java)

    fun getPortfolioTrn(trnId: String): Collection<Trn> {
        val trn = trnRepository.findById(trnId)
        if (trn.isEmpty || !portfolioService.canView(trn.get().portfolio)) {
            throw NotFoundException("Trn not found: $trnId")
        }
        val result = trn.map { transaction: Trn -> trnPostProcessor.postProcess(setOf(transaction)) }
        return result.get()
    }

    fun findForPortfolio(
        portfolioId: String,
        tradeDate: LocalDate
    ): Collection<Trn> {
        val portfolio = portfolioService.find(portfolioId)
        return findForPortfolio(portfolio, tradeDate)
    }

    fun findForPortfolio(
        portfolio: Portfolio,
        tradeDate: LocalDate
    ): Collection<Trn> {
        val results =
            trnRepository.findByPortfolioId(
                portfolio.id,
                tradeDate,
                TrnStatus.SETTLED
            )
        log.trace("trns: ${results.size}, portfolio: ${portfolio.code}, asAt: $tradeDate")
        return trnPostProcessor.postProcess(results)
    }

    /**
     * Find transactions for a portfolio with a specific status.
     */
    fun findByStatus(
        portfolioId: String,
        status: TrnStatus
    ): Collection<Trn> {
        val portfolio = portfolioService.find(portfolioId)
        val results = trnRepository.findByPortfolioIdAndStatus(portfolio.id, status)
        log.trace("trns: ${results.size}, portfolio: ${portfolio.code}, status: $status")
        return trnPostProcessor.postProcess(results)
    }

    /**
     * Get the Cash Ladder for a specific cash asset in a portfolio.
     */
    fun getCashLadder(
        portfolioId: String,
        cashAssetId: String
    ): Collection<Trn> {
        val portfolio = portfolioService.find(portfolioId)
        val today = dateUtils.date
        val results =
            trnRepository.findByPortfolioIdAndCashAssetId(
                portfolio.id,
                cashAssetId,
                today,
                TrnStatus.SETTLED
            )
        log.trace("cash ladder: ${results.size} trns for portfolio: ${portfolio.code}, cashAsset: $cashAssetId")
        return trnPostProcessor.postProcess(results.toList())
    }

    /**
     * PROPOSED DEPOSIT / WITHDRAWAL legs for a portfolio as at a date. Resolves
     * the portfolio for authorisation, then post-processes the legs so the
     * consumer (svc-position earmark) sees the same shape as other finders.
     */
    fun findProposedCash(
        portfolioId: String,
        asAt: LocalDate
    ): Collection<Trn> {
        val portfolio = portfolioService.find(portfolioId)
        return trnPostProcessor.postProcess(
            trnRepository.findProposedCashLegs(portfolio.id, asAt).toList()
        )
    }

    /**
     * Find all transactions for a portfolio that belong to a specific rebalance model.
     */
    fun findByPortfolioAndModel(
        portfolioId: String,
        modelId: String
    ): Collection<Trn> {
        val portfolio = portfolioService.find(portfolioId)
        val results =
            trnRepository.findByPortfolioIdAndModelId(
                portfolio.id,
                modelId,
                TrnStatus.SETTLED
            )
        log.trace("trns: ${results.size}, portfolio: ${portfolio.code}, model: $modelId")
        return trnPostProcessor.postProcess(results.toList())
    }
}