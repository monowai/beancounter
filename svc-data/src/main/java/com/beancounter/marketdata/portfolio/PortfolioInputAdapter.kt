package com.beancounter.marketdata.portfolio

import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.marketdata.currency.CurrencyService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PortfolioInputAdapter internal constructor(private val currencyService: CurrencyService) {
    fun prepare(owner: SystemUser, portfolios: Collection<PortfolioInput>): Collection<Portfolio> {
        val results: MutableCollection<Portfolio> = ArrayList()
        for (portfolio in portfolios) {
            results.add(prepare(owner, portfolio))
        }
        return results
    }

    private fun prepare(owner: SystemUser, portfolioInput: PortfolioInput): Portfolio {
        log.debug("Creating for {}", owner.id)
        return Portfolio(
            KeyGenUtils.format(UUID.randomUUID()),
            portfolioInput.code.toUpperCase(),
            portfolioInput.name,
            currencyService.getCode(portfolioInput.currency)!!,
            currencyService.getCode(portfolioInput.base)!!,
            owner
        )
    }

    fun fromInput(data: PortfolioInput, existing: Portfolio): Portfolio {
        return Portfolio(
            existing.id,
            data.code.toUpperCase(),
            data.name,
            currencyService.getCode(data.currency)!!,
            currencyService.getCode(data.base)!!,
            existing.owner
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(PortfolioInputAdapter::class.java)
    }
}
