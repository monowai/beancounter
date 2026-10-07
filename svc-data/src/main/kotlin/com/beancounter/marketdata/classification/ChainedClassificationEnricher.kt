package com.beancounter.marketdata.classification

import com.beancounter.common.model.Asset

/**
 * Orders several [ClassificationEnricher]s: an asset goes to the first one whose [canEnrich]
 * accepts it, and that enricher's answer is final. A NO_DATA from it does not fall through to
 * the next - falling through would make the stored provider depend on upstream coverage on the
 * day, so an asset could flip between standards run to run. Keep it deterministic; an operator
 * who wants Alpha for everything SEC lacks orders the CSV `sec,alpha` and SEC declines (via
 * [canEnrich]) the ETFs and non-US listings it never covers.
 */
class ChainedClassificationEnricher(
    private val delegates: List<ClassificationEnricher>
) : ClassificationEnricher {
    override fun canEnrich(asset: Asset): Boolean = delegates.any { it.canEnrich(asset) }

    override fun isEtf(asset: Asset): Boolean = ClassificationEnricher.categoryIsEtf(asset)

    override fun isEquity(asset: Asset): Boolean = ClassificationEnricher.categoryIsEquity(asset)

    override fun enrichClassification(asset: Asset): EnrichmentResult {
        val enricher = delegates.firstOrNull { it.canEnrich(asset) }
        return if (enricher == null) EnrichmentResult.NO_DATA else enricher.enrichClassification(asset)
    }
}