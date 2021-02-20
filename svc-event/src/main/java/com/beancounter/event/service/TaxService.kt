package com.beancounter.event.service

import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.HashMap

@Service
class TaxService {
    private val rates: MutableMap<String, BigDecimal> = HashMap()
    fun getRate(code: String?): BigDecimal {
        return rates[code] ?: return BigDecimal.ZERO
    }

    init {
        rates["USD"] = BigDecimal(".30")
    }
}
