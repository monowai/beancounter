package com.beancounter.common.contracts

import com.beancounter.common.model.Currency
import org.springframework.boot.context.properties.ConstructorBinding

data class CurrencyResponse @ConstructorBinding constructor(override var data: Iterable<Currency>) :
    Payload<Iterable<Currency>>
