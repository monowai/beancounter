package com.beancounter.marketdata.registration

import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.portfolio.PortfolioRepository
import com.beancounter.marketdata.trn.TrnRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Component

/**
 * Erases an owner's portfolios with their transactions: trns first, then the
 * portfolios they belong to. Joins the caller's transaction.
 */
@Component
@Transactional
class PortfolioErasure(
    private val portfolioRepository: PortfolioRepository,
    private val trnRepository: TrnRepository
) {
    fun count(owner: SystemUser): Int = portfolioRepository.findByOwner(owner).count()

    /**
     * Returns the number of portfolios erased.
     */
    fun erase(owner: SystemUser): Int {
        val portfolios = portfolioRepository.findByOwner(owner).toList()
        portfolios.forEach { portfolio ->
            trnRepository.deleteByPortfolioId(portfolio.id)
        }
        portfolioRepository.deleteByOwnerId(owner.id)
        return portfolios.size
    }
}