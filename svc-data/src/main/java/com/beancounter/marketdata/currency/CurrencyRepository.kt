package com.beancounter.marketdata.currency

import com.beancounter.common.model.Currency
import org.springframework.data.repository.CrudRepository

interface CurrencyRepository : CrudRepository<Currency, String> {
    fun findAllByOrderByCodeAsc(): Iterable<Currency>
}
