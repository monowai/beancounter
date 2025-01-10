package com.beancounter.marketdata.currency

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Currency
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Service
import java.util.Locale
import java.util.Optional

/**
 * Verification of Market related functions.
 *
 * @author mikeh
 * @since 2019-03-19
 */
@Service
@Transactional
@Import(CurrencyConfig::class)
class CurrencyService(
    val currencyConfig: CurrencyConfig
) {
    @Autowired
    private lateinit var currencyRepository: CurrencyRepository

    var currencies: Iterable<Currency> = listOf()

    fun persist() {
        if (currencies.toList().isEmpty() && !currencyConfig.values.isEmpty()) {
            log.info(
                "Persisting {} default currencies",
                currencyConfig.values.size
            )
            currencies = currencyRepository.saveAll(currencyConfig.values)
            for (currency in currencies) {
                log.trace(
                    "Persisted {}",
                    currency
                )
            }
        }
    }

    /**
     * Resolves a currency via its ISO code (AK).
     *
     * @param code non-null code
     * @return resolved currency
     */
    @Cacheable("currency.code")
    fun getCode(code: String): Currency {
        val result = currencyRepository.findById(code.uppercase(Locale.getDefault()))
        if (result.isPresent) {
            return result.get()
        }
        throw BusinessException("$code is an unknown currency")
    }

    fun currencies(): Iterable<Currency> = currencyRepository.findAllByOrderByCodeAsc()

    @Cacheable("currency.all")
    fun currenciesAs(): String {
        val values = currencies()
        var result: java.lang.StringBuilder? = null
        for ((code) in values) {
            if (result == null) {
                result =
                    Optional
                        .ofNullable(code)
                        .map { str: String? -> StringBuilder(str) }
                        .orElse(null)
            } else if (code != currencyConfig.base) {
                result.append(",").append(code)
            }
        }
        return result.toString()
    }

    companion object {
        private val log = LoggerFactory.getLogger(CurrencyService::class.java)
    }
}