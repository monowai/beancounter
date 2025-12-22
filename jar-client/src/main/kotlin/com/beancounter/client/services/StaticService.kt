package com.beancounter.client.services

import com.beancounter.auth.TokenService
import com.beancounter.client.MarketService
import com.beancounter.common.contracts.CurrencyResponse
import com.beancounter.common.contracts.MarketResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import io.github.resilience4j.retry.annotation.Retry
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpHeaders
import org.springframework.lang.NonNull
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.util.Locale

/**
 * Client side access to static configuration business data.
 */
@Service
class StaticService(
    @Qualifier("bcDataRestClient")
    private val restClient: RestClient,
    private val tokenService: TokenService
) : MarketService {
    @NonNull
    @Retry(name = "data")
    override fun getMarkets(): MarketResponse =
        restClient
            .get()
            .uri("/api/markets")
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body(MarketResponse::class.java)
            ?: throw BusinessException("Failed to retrieve markets")

    val currencies: CurrencyResponse
        get() =
            restClient
                .get()
                .uri("/api/currencies")
                .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
                .retrieve()
                .body(CurrencyResponse::class.java)
                ?: throw BusinessException("Failed to retrieve currencies")

    fun getCurrency(currencyCode: String?): Currency? {
        if (currencyCode == null) {
            return null
        }
        val currencies = currencies.data
        for (currency in currencies) {
            if (currency.code.equals(
                    currencyCode,
                    ignoreCase = true
                )
            ) {
                return currency
            }
        }
        throw BusinessException(
            String.format(
                "Unable to resolve the currency %s",
                currencyCode
            )
        )
    }

    @Cacheable("market")
    override fun getMarket(
        @NonNull marketCode: String
    ): Market {
        val response =
            restClient
                .get()
                .uri("/api/markets/{code}", marketCode.uppercase(Locale.getDefault()))
                .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
                .retrieve()
                .body(MarketResponse::class.java)

        val data = response?.data
        if (data.isNullOrEmpty()) {
            throw BusinessException("Unable to resolve market code $marketCode")
        }
        return data.iterator().next()
    }
}