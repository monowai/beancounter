package com.beancounter.marketdata.portfolio

import com.beancounter.auth.server.AuthConstants
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.Portfolio
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.registration.SystemUserService
import com.beancounter.marketdata.trn.TrnRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.function.Consumer
import javax.transaction.Transactional

/**
 * Server side portfolio activities.
 */
@Service
class PortfolioService internal constructor(
    private val portfolioInputAdapter: PortfolioInputAdapter,
    private val portfolioRepository: PortfolioRepository,
    private val trnRepository: TrnRepository,
    private val systemUserService: SystemUserService,
    private val dateUtils: DateUtils
) {

    fun save(portfolios: Collection<PortfolioInput>): Collection<Portfolio> {
        val owner = systemUserService.getOrThrow
        val results: MutableCollection<Portfolio> = ArrayList()
        portfolioRepository.saveAll(
            portfolioInputAdapter.prepare(owner, portfolios)
        ).forEach(Consumer { e: Portfolio -> results.add(e) })
        return results
    }

    fun canView(found: Portfolio): Boolean {
        val systemUser = systemUserService.getOrThrow
        return systemUser.id == AuthConstants.OAUTH_M2M || found.owner!!.id == systemUser.id
    }

    val portfolios: Collection<Portfolio>
        get() {
            val systemUser = systemUserService.getOrThrow
            val results: MutableCollection<Portfolio> = ArrayList()
            val portfolios = portfolioRepository.findByOwner(systemUser)
            for (portfolio in portfolios) {
                results.add(portfolio)
            }
            return results
        }

    /**
     * Confirms if the requested portfolio is known. Service side call.
     *
     * @param id pk
     * @return exists or not
     */
    fun verify(id: String): Boolean {
        val found = portfolioRepository.findById(id)
        return found.isPresent
    }

    fun find(id: String): Portfolio {
        val found = portfolioRepository.findById(id)
        val portfolio = found.orElseThrow { BusinessException(String.format("Could not find a portfolio with ID %s", id)) }
        if (canView(portfolio)) {
            return portfolio
        }
        throw BusinessException(String.format("Could not find a portfolio with ID %s", id))
    }

    fun findByCode(code: String): Portfolio {
        val systemUser = systemUserService.getOrThrow
        log.trace("Searching on behalf of {}", systemUser.id)
        val found = portfolioRepository
            .findByCodeAndOwner(code.toUpperCase(), systemUser)
        val portfolio = found.orElseThrow {
            BusinessException(
                String.format(
                    "Could not find a portfolio with code %s owned by %s",
                    code,
                    systemUser.id
                )
            )
        }
        if (canView(portfolio)) {
            return portfolio
        }
        throw BusinessException(String.format("Could not find a portfolio with code %s", code))
    }

    @Transactional
    fun update(id: String, portfolioInput: PortfolioInput?): Portfolio {
        val existing = find(id)
        return portfolioRepository.save(portfolioInputAdapter.fromInput(portfolioInput!!, existing))
    }

    @Transactional
    fun delete(id: String) {
        val portfolio = find(id)
        trnRepository.deleteByPortfolioId(portfolio.id)
        portfolioRepository.delete(portfolio)
    }

    fun findWhereHeld(assetId: String?, tradeDate: LocalDate?): PortfoliosResponse {
        val recordDate = tradeDate ?: dateUtils.getDate(dateUtils.today())
        val portfolios = portfolioRepository
            .findDistinctPortfolioByAssetIdAndTradeDate(assetId!!, recordDate)
        log.trace("Found {} notional holders for assetId: {}", portfolios.size, assetId)
        return PortfoliosResponse(portfolios)
    }

    companion object {
        private val log = LoggerFactory.getLogger(PortfolioService::class.java)
    }
}
