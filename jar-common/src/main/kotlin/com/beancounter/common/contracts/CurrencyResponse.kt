package com.beancounter.common.contracts

import com.beancounter.common.model.Currency

/**
 * Beancounter response to a currency query.
 */
data class CurrencyResponse constructor(override var data: Iterable<Currency>) :
    Payload<Iterable<Currency>>
