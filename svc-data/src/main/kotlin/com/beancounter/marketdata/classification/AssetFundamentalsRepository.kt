package com.beancounter.marketdata.classification

import com.beancounter.common.model.AssetFundamentals
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param

/** One [AssetFundamentals] row per asset; `save` on an existing id merges in place. */
interface AssetFundamentalsRepository : CrudRepository<AssetFundamentals, String> {
    /** Bulk delete that is a no-op when the asset never had a snapshot, like the sibling repositories. */
    @Modifying
    @Query("DELETE FROM AssetFundamentals af WHERE af.assetId = :assetId")
    fun deleteByAssetId(
        @Param("assetId") assetId: String
    )
}