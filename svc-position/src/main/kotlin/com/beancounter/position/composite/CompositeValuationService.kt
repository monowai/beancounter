package com.beancounter.position.composite

import com.beancounter.client.Assets
import com.beancounter.common.model.Position
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Strategy registry. Looks up an asset's config, picks the first
 * [CompositeValuation] strategy that supports it and returns the synthesised
 * [Position]. Returns null when the asset has no composite policy so callers
 * can fall back to the regular position lookup.
 */
@Service
class CompositeValuationService(
    private val configClient: AssetConfigClient,
    private val assets: Assets,
    private val strategies: List<CompositeValuation>
) {
    fun valueFor(
        assetId: String,
        asAt: LocalDate
    ): Position? {
        val config = configClient.find(assetId)
        val strategy = strategies.firstOrNull { it.supports(config) } ?: return null
        val asset = assets.find(assetId)
        return strategy.value(asset, config, asAt)
    }
}