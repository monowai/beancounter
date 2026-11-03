package com.beancounter.position.marketvalue

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.client.services.ClassificationClient
import com.beancounter.client.services.PriceService
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.model.Positions
import com.beancounter.position.valuation.runAs
import io.sentry.kotlin.SentryContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import kotlin.coroutines.cancellation.CancellationException

/**
 * The market data a valuation needs from bc-data: prices, FX rates and
 * classifications for a set of positions, read at once.
 */
@Component
class ValuationDataSource(
    private val priceService: PriceService,
    private val fxRateService: FxService,
    private val fxUtils: FxUtils,
    private val classificationClient: ClassificationClient,
    private val tokenService: TokenService,
    /**
     * Whether to include zero-quantity positions when fetching prices.
     *
     * When false (default), positions with zero quantity (sold out) are excluded from
     * price requests, reducing unnecessary API calls to market data providers.
     * When true, prices are fetched for all positions including those fully sold.
     *
     * Can be configured via BEANCOUNTER_VALUATION_INCLUDE_ZERO_QUANTITY environment variable
     * or beancounter.valuation.includeZeroQuantity property.
     */
    @param:Value("\${beancounter.valuation.includeZeroQuantity:false}")
    private val includeZeroQuantity: Boolean = false
) {
    /** [live] reads live (delayed) quotes while each market is in session. */
    suspend fun read(
        positions: Positions,
        token: String = tokenService.bearerToken,
        live: Boolean = false
    ): ValuationData {
        // Blocking clients only overlap on the IO pool; runBlocking alone keeps every
        // async on the request thread, one call after another.
        val caller = SecurityContextHolder.getContext()
        return withContext(SentryContext() + Dispatchers.IO) {
            try {
                val fxRequest =
                    fxUtils.buildRequest(
                        positions.portfolio.base,
                        positions
                    )
                val priceRequest =
                    PriceRequest.of(
                        positions.asAt,
                        positions,
                        includeZeroQuantity = includeZeroQuantity
                    )
                val priceDeferred =
                    async {
                        caller.runAs {
                            if (live) {
                                priceService.getLivePrices(priceRequest, token)
                            } else {
                                priceService.getPrices(priceRequest, token)
                            }
                        }
                    }
                val fxDeferred =
                    async {
                        caller.runAs {
                            fxRateService.getRates(
                                fxRequest,
                                token
                            )
                        }
                    }
                val classificationDeferred =
                    async {
                        caller.runAs {
                            classificationClient.getClassifications(
                                positions.positions.values.map { it.asset.id }
                            )
                        }
                    }

                ValuationData(
                    priceResponse = priceDeferred.await(),
                    fxResponse = fxDeferred.await(),
                    classifications = classificationDeferred.await()
                )
            } catch (e: CancellationException) {
                throw IllegalStateException(
                    "Thread was interrupted while fetching market data.",
                    e
                )
            }
        }
    }
}