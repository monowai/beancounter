package com.beancounter.marketdata.assets

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.model.ShareStatus
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.portfolio.PortfolioShareRepository
import com.beancounter.marketdata.registration.SystemUserService
import com.beancounter.marketdata.trn.TrnRepository
import org.springframework.stereotype.Service

/**
 * Access-control checks for user-owned (private) assets.
 *
 * Writes are owner-only ([verifyOwnership]). Reads additionally allow any
 * user holding an ACTIVE portfolio share over a portfolio with transactions
 * for the asset ([verifyReadAccess]) — this is what lets a shared-plan
 * viewer resolve pension/policy configs without owning the asset.
 */
@Service
class AssetAccessControl(
    private val assetRepository: AssetRepository,
    private val systemUserService: SystemUserService,
    private val trnRepository: TrnRepository,
    private val portfolioShareRepository: PortfolioShareRepository
) {
    /**
     * Verify the current user owns the specified asset.
     */
    fun verifyOwnership(assetId: String) {
        val user = activeUser()
        val asset = findAsset(assetId)
        if (asset.systemUser?.id != user.id) {
            throw BusinessException("Asset not owned by current user")
        }
    }

    /**
     * Read-only counterpart to [verifyOwnership]. Allows the asset owner OR
     * any user with an ACTIVE portfolio share covering a portfolio that holds
     * transactions for this asset. Write paths must still call
     * [verifyOwnership].
     */
    fun verifyReadAccess(assetId: String) {
        val user = activeUser()
        val asset = findAsset(assetId)
        if (asset.systemUser?.id == user.id) return
        if (hasSharedPortfolioReadAccess(assetId, user)) return
        throw BusinessException("Asset not accessible to current user")
    }

    private fun activeUser(): SystemUser =
        systemUserService.getActiveUser()
            ?: throw BusinessException(SystemUserService.USER_NOT_AUTHENTICATED)

    private fun findAsset(assetId: String) =
        assetRepository.findById(assetId).orElseThrow {
            NotFoundException("Asset not found: $assetId")
        }

    private fun hasSharedPortfolioReadAccess(
        assetId: String,
        user: SystemUser
    ): Boolean {
        val sharedPortfolioIds =
            portfolioShareRepository
                .findBySharedWithAndStatus(
                    user,
                    ShareStatus.ACTIVE
                ).mapNotNull { it.portfolio?.id }
                .toSet()
        if (sharedPortfolioIds.isEmpty()) return false
        val assetIdsInSharedPortfolios =
            trnRepository
                .findDistinctAssetIdsByPortfolioIds(sharedPortfolioIds)
                .toSet()
        return assetId in assetIdsInSharedPortfolios
    }
}