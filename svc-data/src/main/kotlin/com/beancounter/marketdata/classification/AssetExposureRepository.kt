package com.beancounter.marketdata.classification

import com.beancounter.common.model.AssetExposure
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param

interface AssetExposureRepository : CrudRepository<AssetExposure, String> {
    @Query("SELECT ae FROM AssetExposure ae WHERE ae.asset.id = :assetId")
    fun findByAssetId(
        @Param("assetId") assetId: String
    ): List<AssetExposure>

    @Modifying
    @Query("DELETE FROM AssetExposure ae WHERE ae.asset.id = :assetId")
    fun deleteByAssetId(
        @Param("assetId") assetId: String
    )

    @Query("SELECT ae FROM AssetExposure ae WHERE ae.asset.id IN :assetIds")
    fun findByAssetIdIn(
        @Param("assetIds") assetIds: List<String>
    ): List<AssetExposure>

    @Query("SELECT COUNT(ae) FROM AssetExposure ae WHERE ae.item.id = :itemId")
    fun countByItemId(
        @Param("itemId") itemId: String
    ): Int

    @Modifying
    @Query("DELETE FROM AssetExposure ae WHERE ae.item.id = :itemId")
    fun deleteByItemId(
        @Param("itemId") itemId: String
    )
}