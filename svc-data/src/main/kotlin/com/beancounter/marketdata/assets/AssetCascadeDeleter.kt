package com.beancounter.marketdata.assets

import com.beancounter.marketdata.broker.BrokerSettlementAccountRepository
import com.beancounter.marketdata.classification.AssetClassificationRepository
import com.beancounter.marketdata.classification.AssetExposureRepository
import com.beancounter.marketdata.classification.AssetHoldingRepository
import com.beancounter.marketdata.providers.MarketDataRepo
import com.beancounter.marketdata.trn.TrnRepository
import org.springframework.stereotype.Service

/**
 * Handles cascade deletion of all dependent data for an asset.
 * Deletes in FK dependency order to avoid constraint violations.
 */
@Service
class AssetCascadeDeleter(
    private val trnRepository: TrnRepository,
    private val marketDataRepo: MarketDataRepo,
    private val assetClassificationRepository: AssetClassificationRepository,
    private val assetExposureRepository: AssetExposureRepository,
    private val assetHoldingRepository: AssetHoldingRepository,
    private val privateAssetConfigRepository: PrivateAssetConfigRepository,
    private val brokerSettlementAccountRepository: BrokerSettlementAccountRepository
) {
    /**
     * Delete all dependent data for an asset in FK dependency order.
     */
    fun deleteDependents(assetId: String) {
        trnRepository.clearCashAssetReferences(assetId)
        trnRepository.deleteByAssetId(assetId)
        marketDataRepo.deleteByAssetId(assetId)
        assetClassificationRepository.deleteByAssetId(assetId)
        assetExposureRepository.deleteByAssetId(assetId)
        assetHoldingRepository.deleteByAssetId(assetId)
        brokerSettlementAccountRepository.deleteByAccountId(assetId)
        privateAssetConfigRepository.deleteById(assetId)
    }
}