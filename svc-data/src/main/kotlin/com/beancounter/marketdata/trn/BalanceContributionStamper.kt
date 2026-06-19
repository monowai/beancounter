package com.beancounter.marketdata.trn

import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.assets.ContributionFrequency
import com.beancounter.marketdata.assets.PrivateAssetConfig
import com.beancounter.marketdata.assets.PrivateAssetConfigRepository
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.temporal.ChronoUnit

/**
 * Read-time enrichment for BALANCE snapshots of contribution-driven private
 * assets (CPF, pensions).
 *
 * A BALANCE is a point-in-time snapshot of an account whose balance grows
 * mostly from FRESH CONTRIBUTIONS, with interest on top. Without separating
 * the two, svc-position can only treat every balance increase as either all
 * gain (absurd growth) or all principal (no gain). This stamps each non-first
 * snapshot with the contribution recognised since the prior snapshot — derived
 * from the asset's [PrivateAssetConfig.monthlyContribution] — so svc-position
 * can grow cost basis by contributions and surface only interest as gain.
 *
 * The value is an ESTIMATE (the configured contribution, not recorded actuals)
 * and is never persisted — [Trn.contribution] is `@Transient`.
 */
@Component
class BalanceContributionStamper(
    private val privateAssetConfigRepository: PrivateAssetConfigRepository
) {
    fun stamp(trns: Collection<Trn>) {
        val balances = trns.filter { it.trnType == TrnType.BALANCE }
        if (balances.isEmpty()) return

        val configByAsset =
            privateAssetConfigRepository
                .findByAssetIdIn(balances.map { it.asset.id }.distinct())
                .associateBy { it.assetId }

        // Each (portfolio, asset) is an independent snapshot series.
        balances
            .groupBy { it.portfolio.id to it.asset.id }
            .forEach { (key, series) ->
                val monthly = monthlyEquivalent(configByAsset[key.second]) ?: return@forEach
                val ordered = series.sortedWith(compareBy({ it.tradeDate }, { it.createdAt }))
                // First snapshot is the starting principal — leave contribution
                // null so svc-position pins cost at that balance (gain 0).
                for (i in 1 until ordered.size) {
                    val months =
                        ChronoUnit.MONTHS
                            .between(
                                ordered[i - 1].tradeDate.withDayOfMonth(1),
                                ordered[i].tradeDate.withDayOfMonth(1)
                            ).coerceAtLeast(0)
                    if (months > 0) {
                        ordered[i].contribution = monthly.multiply(BigDecimal(months))
                    }
                }
            }
    }

    /** Monthly-equivalent contribution, or null when none is configured. */
    private fun monthlyEquivalent(config: PrivateAssetConfig?): BigDecimal? {
        val amount = config?.monthlyContribution ?: return null
        if (amount.signum() <= 0) return null
        return if (config.contributionFrequency == ContributionFrequency.ANNUAL) {
            amount.divide(BigDecimal(12), 4, RoundingMode.HALF_UP)
        } else {
            amount
        }
    }
}