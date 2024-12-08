package com.beancounter.client.services

import com.beancounter.auth.TokenService
import com.beancounter.client.MarketService
import com.beancounter.common.contracts.CurrencyResponse
import com.beancounter.common.contracts.MarketResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import feign.FeignException
import io.github.resilience4j.retry.annotation.Retry
import org.springframework.cache.annotation.Cacheable
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.MediaType
import org.springframework.lang.NonNull
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import java.util.Locale

/**
 * Client side access to static configuration business data.
 */
@Service
class StaticService(
    val staticGateway: StaticGateway,
    private val tokenService: TokenService,
) : MarketService {
    @NonNull
    @Retry(name = "data")
    override fun getMarkets(): MarketResponse = staticGateway.getMarkets(tokenService.bearerToken)

    val currencies: CurrencyResponse
        get() = staticGateway.getCurrencies(tokenService.bearerToken)

    fun getCurrency(currencyCode: String?): Currency? {
        if (currencyCode == null) {
            return null
        }
        val currencies = currencies.data
        for (currency in currencies) {
            if (currency.code.equals(
                    currencyCode,
                    ignoreCase = true,
                )
            ) {
                return currency
            }
        }
        throw BusinessException(
            String.format(
                "Unable to resolve the currency %s",
                currencyCode,
            ),
        )
    }

    @Cacheable("market")
    override fun getMarket(
        @NonNull marketCode: String,
    ): Market =
        try {
            val (data) =
                staticGateway.getMarketByCode(
                    tokenService.bearerToken,
                    marketCode.uppercase(Locale.getDefault()),
                )
            if (data.isNullOrEmpty()) {
                throw BusinessException("Unable to resolve market code $marketCode")
            }
            data.iterator().next()
        } catch (re: FeignException) {
            throw BusinessException("Error resolving market code $marketCode")
        }

    /**
     * API calls to the BC-DATA service to obtain the data.
     */
    @FeignClient(
        name = "static",
        url = "\${marketdata.url:http://localhost:9510}",
    )
    interface StaticGateway {
        @GetMapping(
            value = ["/api/markets"],
            produces = [MediaType.APPLICATION_JSON_VALUE],
        )
        fun getMarkets(
            @RequestHeader("Authorization") bearerToken: String?,
        ): MarketResponse

        @GetMapping(
            value = ["/api/markets/{code}"],
            produces = [MediaType.APPLICATION_JSON_VALUE],
        )
        fun getMarketByCode(
            @RequestHeader("Authorization") bearerToken: String?,
            @PathVariable code: String?,
        ): MarketResponse

        @GetMapping(
            value = ["/api/currencies"],
            produces = [MediaType.APPLICATION_JSON_VALUE],
        )
        fun getCurrencies(
            @RequestHeader("Authorization") bearerToken: String?,
        ): CurrencyResponse
    }
}
