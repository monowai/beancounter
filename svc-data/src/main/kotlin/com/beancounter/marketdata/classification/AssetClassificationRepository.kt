package com.beancounter.marketdata.classification

import com.beancounter.common.model.AssetClassification
import com.beancounter.common.model.ClassificationLevel
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import java.util.Optional

interface AssetClassificationRepository : CrudRepository<AssetClassification, String> {
    @Query("SELECT ac FROM AssetClassification ac WHERE ac.asset.id = :assetId")
    fun findByAssetId(
        @Param("assetId") assetId: String
    ): List<AssetClassification>

    @Query("SELECT ac FROM AssetClassification ac WHERE ac.asset.id = :assetId AND ac.level = :level")
    fun findByAssetIdAndLevel(
        @Param("assetId") assetId: String,
        @Param("level") level: ClassificationLevel
    ): Optional<AssetClassification>

    @Query("SELECT ac FROM AssetClassification ac WHERE ac.asset.id IN :assetIds")
    fun findByAssetIdIn(
        @Param("assetIds") assetIds: List<String>
    ): List<AssetClassification>

    @Modifying
    @Query("DELETE FROM AssetClassification ac WHERE ac.asset.id = :assetId")
    fun deleteByAssetId(
        @Param("assetId") assetId: String
    )

    @Query("SELECT COUNT(ac) FROM AssetClassification ac WHERE ac.item.id = :itemId")
    fun countByItemId(
        @Param("itemId") itemId: String
    ): Int

    @Modifying
    @Query("DELETE FROM AssetClassification ac WHERE ac.item.id = :itemId")
    fun deleteByItemId(
        @Param("itemId") itemId: String
    )
}