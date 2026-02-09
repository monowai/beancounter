package com.beancounter.marketdata.assets

import com.beancounter.common.model.AccountingType
import com.beancounter.common.model.Currency
import org.springframework.data.repository.CrudRepository
import java.util.Optional

interface AccountingTypeRepository : CrudRepository<AccountingType, String> {
    fun findByCategoryAndCurrency(
        category: String,
        currency: Currency
    ): Optional<AccountingType>
}