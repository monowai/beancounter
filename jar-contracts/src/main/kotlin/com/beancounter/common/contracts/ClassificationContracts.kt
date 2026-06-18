package com.beancounter.common.contracts

import com.beancounter.common.model.AssetClassification
import com.beancounter.common.model.AssetExposure
import com.beancounter.common.model.AssetHolding
import java.math.BigDecimal

/**
 * Sector exposure for a single sector in a portfolio.
 */
data class SectorExposure(
    val sector: String,
    val marketValue: BigDecimal,
    val percentage: BigDecimal
)

/**
 * Aggregated sector exposure data for a portfolio or set of portfolios.
 */
data class SectorExposureData(
    val exposures: List<SectorExposure> = emptyList(),
    val totalValue: BigDecimal = BigDecimal.ZERO,
    val currency: String = "USD"
)

/**
 * Response wrapper for sector exposure data.
 */
data class SectorExposureResponse(
    override val data: SectorExposureData = SectorExposureData()
) : Payload<SectorExposureData>

/**
 * Response wrapper for asset classifications.
 */
data class AssetClassificationsResponse(
    override val data: List<AssetClassification> = emptyList()
) : Payload<List<AssetClassification>>

/**
 * Response wrapper for asset exposures.
 */
data class AssetExposuresResponse(
    override val data: List<AssetExposure> = emptyList()
) : Payload<List<AssetExposure>>

/**
 * Response wrapper for asset holdings (top holdings within an ETF).
 */
data class AssetHoldingsResponse(
    override val data: List<AssetHolding> = emptyList()
) : Payload<List<AssetHolding>>

/**
 * Result of a backfill operation.
 */
data class BackfillResult(
    val processed: Int = 0,
    val errors: Int = 0,
    val total: Int = 0
)

/**
 * Response wrapper for backfill result.
 */
data class BackfillResponse(
    override val data: BackfillResult = BackfillResult()
) : Payload<BackfillResult>

/**
 * Classification summary for a single asset - simplified for frontend use.
 */
data class AssetClassificationSummary(
    val assetId: String,
    val sector: String? = null,
    val industry: String? = null
)

/**
 * Request for bulk classification lookup.
 */
data class BulkClassificationRequest(
    val assetIds: List<String>
)

/**
 * Response for bulk classification lookup - maps asset IDs to their classifications.
 */
data class BulkClassificationResponse(
    override val data: Map<String, AssetClassificationSummary> = emptyMap()
) : Payload<Map<String, AssetClassificationSummary>>

/**
 * Request to manually set a sector for an asset.
 * Used for user-defined categories like "Income", "Diversified", "Index".
 */
data class ManualClassificationRequest(
    val sector: String,
    val industry: String? = null
)

/**
 * Response for manual classification - returns the updated classification.
 */
data class ManualClassificationResponse(
    override val data: AssetClassificationSummary
) : Payload<AssetClassificationSummary>

/**
 * Simplified sector representation for admin UI.
 */
data class SectorInfo(
    val code: String,
    val name: String,
    val standard: String
)

/**
 * Response for listing all sectors.
 */
data class SectorsResponse(
    override val data: List<SectorInfo> = emptyList()
) : Payload<List<SectorInfo>>

/**
 * Result of deleting a sector.
 */
data class DeleteSectorResult(
    val sectorCode: String,
    val affectedAssets: Int = 0
)

/**
 * Response for deleting a sector.
 */
data class DeleteSectorResponse(
    override val data: DeleteSectorResult
) : Payload<DeleteSectorResult>