package com.beancounter.marketdata.portfolio

import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.marketdata.currency.CurrencyService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Locale

/**
 * Adapts PortfolioInput objects to Portfolio objects
 */
@Service
class PortfolioInputAdapter internal constructor(
    private val currencyService: CurrencyService,
    private val keyGenUtils: KeyGenUtils
) {
    fun prepare(
        owner: SystemUser,
        portfolios: Collection<PortfolioInput>
    ): Collection<Portfolio> {
        val results: MutableCollection<Portfolio> = mutableListOf()
        for (portfolio in portfolios) {
            results.add(
                prepare(
                    owner,
                    portfolio
                )
            )
        }
        return results
    }

    private fun prepare(
        owner: SystemUser,
        portfolioInput: PortfolioInput
    ): Portfolio {
        log.trace(
            "Creating for {}",
            owner.id
        )
        return Portfolio(
            keyGenUtils.id,
            portfolioInput.code.uppercase(Locale.getDefault()),
            portfolioInput.name,
            active = portfolioInput.active,
            currency = currencyService.getCode(portfolioInput.currency),
            base = currencyService.getCode(portfolioInput.base),
            owner = owner,
            cashPortfolioId = portfolioInput.cashPortfolioId
        )
    }

    fun fromInput(
        data: PortfolioInput,
        existing: Portfolio
    ): Portfolio =
        existing.copy(
            code = data.code.uppercase(Locale.getDefault()),
            name = data.name,
            active = data.active,
            currency = currencyService.getCode(data.currency),
            base = currencyService.getCode(data.base),
            cashPortfolioId = data.cashPortfolioId
        )

    companion object {
        private val log = LoggerFactory.getLogger(PortfolioInputAdapter::class.java)
    }
}