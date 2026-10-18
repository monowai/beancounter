package com.beancounter.agent.client

import com.beancounter.auth.TokenService
import com.beancounter.common.contracts.PriceHistoryResponse
import com.beancounter.common.contracts.PricePoint
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetFundamentals
import com.beancounter.common.model.Currency
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import java.math.BigDecimal

/**
 * Thin client for svc-data's watchlist, price-history and fundamentals APIs — only what the agent's
 * watchlist tool needs. The watchlist contract lives in svc-data, so the fields read here
 * are declared locally; unread fields are ignored.
 */
@Service
class WatchlistClient(
    @Qualifier("bcDataRestClient")
    private val restClient: RestClient,
    private val tokenService: TokenService
) {
    fun getWatchlist(): List<WatchlistEntry> =
        restClient
            .get()
            .uri("/watchlist")
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body<WatchlistEnvelope>()
            ?.data
            ?.items
            // An empty answer is a failure, not "nothing watched".
            ?: throw BusinessException("No watchlist response")

    /** Split-adjusted daily closes from [from] to today, oldest first. */
    fun getCloses(
        assetId: String,
        from: String
    ): Collection<PricePoint> =
        restClient
            .get()
            .uri("/prices/{assetId}/history?from={from}", assetId, from)
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body<PriceHistoryResponse>()
            ?.prices
            ?: throw BusinessException("No price history response for asset $assetId")

    /** The latest fiscal-year SEC snapshot, or null when svc-data holds none for [assetId]. */
    fun getFundamentals(assetId: String): AssetFundamentals? =
        try {
            restClient
                .get()
                .uri("/assets/{assetId}/fundamentals", assetId)
                .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
                .retrieve()
                .body<AssetFundamentals>()
        } catch (_: NotFoundException) {
            // Only US filers are snapshotted; anything else has no fundamentals, which is not an error.
            null
        }
}

data class WatchlistEnvelope(
    val data: WatchlistItems
)

data class WatchlistItems(
    val items: List<WatchlistEntry>
)

/** One watched asset as svc-data reports it; percentages are fractions (0.25 = 25%). */
data class WatchlistEntry(
    val asset: Asset,
    val currency: Currency,
    val addedPrice: BigDecimal,
    val addedOn: String,
    val targetPrice: BigDecimal? = null,
    val price: BigDecimal? = null,
    val priceDate: String? = null,
    val changeOnDayPercent: BigDecimal? = null,
    val changeSinceAddedPercent: BigDecimal? = null,
    val toTarget: BigDecimal? = null
)