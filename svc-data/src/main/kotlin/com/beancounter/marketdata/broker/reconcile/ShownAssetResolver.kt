package com.beancounter.marketdata.broker.reconcile

import com.beancounter.common.contracts.AssetSearchResult
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.search.AssetSearchService
import com.beancounter.marketdata.broker.GlobalBroker
import com.beancounter.marketdata.trn.BrokerHoldingPosition
import org.springframework.stereotype.Component
import java.util.Locale

/**
 * The asset a shown holding names: an asset Beancounter has, or a listing the asset search
 * finds that Beancounter does not have yet. Creates nothing.
 */
@Component
class ShownAssetResolver(
    private val assetFinder: AssetFinder,
    private val assetSearchService: AssetSearchService
) {
    /**
     * A market the row names is used as it is. Otherwise, in order: the asset the broker
     * holds under the code, the firm's market when Beancounter lists the code there, the
     * only market Beancounter lists it on, the firm's market when the asset search finds it
     * there, the only market a search of every market finds it on. Several listings and no
     * preference leave the market to the user.
     */
    fun resolve(
        row: ShownHolding,
        firm: GlobalBroker?,
        held: List<BrokerHoldingPosition>
    ): ResolvedAsset {
        val code = beancounterCode(row.code)
        val shownMarket = row.market?.trim().orEmpty()
        if (shownMarket.isNotEmpty()) {
            return ResolvedAsset(shownMarket, onMarket(code, shownMarket), MarketSource.SHOWN)
        }
        val heldAsset = held.singleOrNull { sameSymbol(it.assetCode, code) }
        if (heldAsset != null) {
            val asset = assetFinder.findOrNull(heldAsset.assetId)
            return ResolvedAsset(heldAsset.market, asset?.let { known(it) }, MarketSource.HELD)
        }
        val firmMarket =
            firm
                ?.config
                ?.market
                ?.trim()
                .orEmpty()
        val listed = assetFinder.findListed(code)
        return if (listed.isEmpty()) searched(code, firmMarket) else chosen(listed, firmMarket)
    }

    /** Among the markets Beancounter lists the code on. */
    private fun chosen(
        listed: List<Asset>,
        firmMarket: String
    ): ResolvedAsset {
        val listedOn = listed.map { it.marketCode }.distinct().sorted()
        val atFirm = listed.firstOrNull { it.marketCode.equals(firmMarket, ignoreCase = true) }
        val only = listed.singleOrNull()
        return when {
            atFirm != null -> ResolvedAsset(atFirm.marketCode, known(atFirm), MarketSource.BROKERAGE, listedOn)
            only != null -> ResolvedAsset(only.marketCode, known(only), MarketSource.LISTED, listedOn)
            else -> ResolvedAsset(null, null, null, listedOn)
        }
    }

    /** Beancounter has no listing: ask the asset search, the firm's market first. */
    private fun searched(
        code: String,
        firmMarket: String
    ): ResolvedAsset {
        if (firmMarket.isNotEmpty()) {
            searchOn(code, firmMarket)?.let { return ResolvedAsset(firmMarket, it, MarketSource.BROKERAGE) }
        }
        // Keyed by market: one listing per market, and only results that name one.
        val everywhere =
            assetSearchService
                .searchSymbol(code, GLOBAL_SEARCH)
                .data
                .filter { sameSymbol(it.symbol, code) }
                .mapNotNull { result -> result.market?.takeIf { it.isNotBlank() }?.let { it to result } }
                .distinctBy { (market, _) -> market.uppercase(Locale.ROOT) }
        val listedOn = everywhere.map { (market, _) -> market }.sorted()
        val only = everywhere.singleOrNull()
        return when {
            only != null -> ResolvedAsset(only.first, listing(code, only.second), MarketSource.LISTED, listedOn)
            everywhere.isNotEmpty() || firmMarket.isEmpty() -> ResolvedAsset(null, null, null, listedOn)
            else -> ResolvedAsset(firmMarket, null, MarketSource.BROKERAGE)
        }
    }

    /** The asset on [market]: Beancounter's own, else one the asset search finds there. */
    private fun onMarket(
        code: String,
        market: String
    ): FoundAsset? = assetFinder.findLocally(AssetInput(market, code))?.let { known(it) } ?: searchOn(code, market)

    private fun searchOn(
        code: String,
        market: String
    ): FoundAsset? =
        assetSearchService
            .searchSymbol(code, market)
            .data
            .firstOrNull { sameSymbol(it.symbol, code) && it.market.equals(market, ignoreCase = true) }
            ?.let { listing(code, it) }

    private fun known(asset: Asset) = FoundAsset(asset.id, asset.code, asset.name)

    /** A search result may already be Beancounter's; otherwise it is created under [code] on record. */
    private fun listing(
        code: String,
        result: AssetSearchResult
    ) = FoundAsset(result.assetId, code, result.name)

    companion object {
        /** The asset search's market for a search of every market. */
        private const val GLOBAL_SEARCH = "FIGI"

        /** A statement writes a share class as BRK/B; Beancounter writes BRK.B. */
        fun beancounterCode(shown: String): String = shown.trim().uppercase(Locale.ROOT).replace('/', '.')

        /** Providers punctuate share classes differently: BRK.B, BRK-B, BRK/B. */
        fun sameSymbol(
            left: String,
            right: String
        ): Boolean =
            left.filter { it.isLetterOrDigit() }.equals(right.filter { it.isLetterOrDigit() }, ignoreCase = true)
    }
}

/**
 * An asset a row names.
 *
 * @property id Beancounter's id, or null for a listing Beancounter does not have yet
 * @property code the code Beancounter knows it by, or will create it under
 */
data class FoundAsset(
    val id: String?,
    val code: String,
    val name: String?
)

/** The market a row trades on, the asset it names there, and where the market came from. */
data class ResolvedAsset(
    val market: String?,
    val asset: FoundAsset?,
    val source: MarketSource?,
    val listedOn: List<String> = emptyList()
)