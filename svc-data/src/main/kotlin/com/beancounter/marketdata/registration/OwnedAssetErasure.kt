package com.beancounter.marketdata.registration

import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.assets.AssetRepository
import com.beancounter.marketdata.assets.admin.AssetCascadeDeleter
import com.beancounter.marketdata.broker.BrokerRepository
import com.beancounter.marketdata.broker.BrokerSettlementAccountRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Component

/**
 * Erases an owner's assets and brokers. Joins the caller's transaction.
 *
 * Deletion order respects FK constraints:
 * 1. Settlement accounts. broker_settlement_account has NO-ACTION FKs to both the
 *    cash asset (fk_settlement_account) and the broker (fk_settlement_broker), so
 *    the asset and broker deletes below trip those constraints unless the
 *    settlement rows are gone first.
 * 2. Asset dependents (trns, prices, classifications, watchlist items, ... via
 *    [AssetCascadeDeleter]), then each asset.
 * 3. Brokers, after trns (trn.broker_id FK) and settlement accounts are gone.
 */
@Component
@Transactional
class OwnedAssetErasure(
    private val assetRepository: AssetRepository,
    private val brokerRepository: BrokerRepository,
    private val brokerSettlementAccountRepository: BrokerSettlementAccountRepository,
    private val assetCascadeDeleter: AssetCascadeDeleter
) {
    fun count(owner: SystemUser): OwnedAssets =
        OwnedAssets(
            assets = assetRepository.findBySystemUserId(owner.id).size,
            brokers = brokerRepository.findByOwner(owner).size
        )

    /**
     * Returns the number of assets and brokers erased.
     */
    fun erase(owner: SystemUser): OwnedAssets {
        brokerSettlementAccountRepository.deleteByBrokerOwnerId(owner.id)
        val assets = assetRepository.findBySystemUserId(owner.id)
        assets.forEach { asset ->
            assetCascadeDeleter.deleteDependents(asset.id)
            assetRepository.delete(asset)
        }
        val brokers = brokerRepository.deleteByOwnerId(owner.id).toInt()
        return OwnedAssets(assets = assets.size, brokers = brokers)
    }
}

/**
 * How many assets and brokers an owner holds.
 */
data class OwnedAssets(
    val assets: Int,
    val brokers: Int
)