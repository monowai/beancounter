package com.beancounter.marketdata.currency

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Currency
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.cache.annotation.Cacheable
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
@ConfigurationProperties(prefix = "beancounter.currency")
class CurrencyService {
    final var base: String = "USD"
    var values: Collection<Currency> = arrayListOf()
    var baseCurrency: Currency = Currency(base)

    @Autowired
    private lateinit var currencyRepository: CurrencyRepository

    @PostConstruct
    private fun persist() {
        log.info(
            "Persisting {} default currencies",
            values.size
        )
        if (!values.isEmpty()) {
            val result = currencyRepository.saveAll(this.values)
            for (currency in result) {
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

    @get:Cacheable("currency.all")
    val currencies: Iterable<Currency>
        get() = currencyRepository.findAllByOrderByCodeAsc()

    val currenciesAs: String
        get() {
            val values = currencies
            var result: java.lang.StringBuilder? = null
            for ((code) in values) {
                if (result == null) {
                    result =
                        Optional
                            .ofNullable(code)
                            .map { str: String? -> StringBuilder(str) }
                            .orElse(null)
                } else if (code != base) {
                    result.append(",").append(code)
                }
            }
            return result.toString()
        }

    companion object {
        private val log = LoggerFactory.getLogger(CurrencyService::class.java)
    }
}