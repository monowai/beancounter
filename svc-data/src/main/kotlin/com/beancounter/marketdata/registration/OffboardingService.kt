package com.beancounter.marketdata.registration

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.tax.TaxRateRepository
import com.beancounter.marketdata.watchlist.WatchlistService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service for user offboarding - allows users to remove their data from Beancounter.
 *
 * Deletion order respects FK constraints:
 * 1. Portfolios and their transactions ([PortfolioErasure])
 * 2. Settlement accounts, assets with their dependents, then brokers ([OwnedAssetErasure])
 * 3. TaxRates
 * 4. Watchlist items, UserPreferences
 * 5. SystemUser (deactivated, not deleted)
 */
@Service
@Transactional
class OffboardingService(
    private val systemUserService: SystemUserService,
    private val systemUserRepository: SystemUserRepository,
    private val systemUserCache: SystemUserCache,
    private val portfolioErasure: PortfolioErasure,
    private val ownedAssetErasure: OwnedAssetErasure,
    private val taxRateRepository: TaxRateRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val watchlistService: WatchlistService
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Get summary of user's data for offboarding preview.
     */
    fun getSummary(): OffboardingSummaryResponse {
        val user = activeUser()
        val owned = ownedAssetErasure.count(user)
        return OffboardingSummaryResponse(
            portfolioCount = portfolioErasure.count(user),
            assetCount = owned.assets,
            taxRateCount = taxRateRepository.findAllByOwnerId(user.id).size,
            brokerCount = owned.brokers
        )
    }

    /**
     * Delete all user-owned assets and their associated data.
     * Cascades to: transactions, market data, settlement accounts, brokers
     */
    fun deleteUserAssets(): OffboardingResult {
        val user = activeUser()
        if (ownedAssetErasure.count(user).assets == 0) {
            return OffboardingResult(
                success = true,
                deletedCount = 0,
                type = "assets",
                message = "No assets to delete"
            )
        }
        val erased = ownedAssetErasure.erase(user)
        log.info("Deleted {} assets, {} brokers for user {}", erased.assets, erased.brokers, user.id)
        return OffboardingResult(
            success = true,
            deletedCount = erased.assets,
            type = "assets"
        )
    }

    /**
     * Delete all user's portfolios and their transactions.
     */
    fun deleteUserPortfolios(): OffboardingResult {
        val user = activeUser()
        if (portfolioErasure.count(user) == 0) {
            return OffboardingResult(
                success = true,
                deletedCount = 0,
                type = "portfolios",
                message = "No portfolios to delete"
            )
        }
        val portfolios = portfolioErasure.erase(user)
        log.info("Deleted {} portfolios for user {}", portfolios, user.id)
        return OffboardingResult(
            success = true,
            deletedCount = portfolios,
            type = "portfolios"
        )
    }

    /**
     * Delete all user's wealth data: portfolios, assets, and their transactions.
     * This is a combined operation for the Wealth step in offboarding.
     */
    fun deleteUserWealth(): OffboardingResult {
        val user = activeUser()
        val portfolios = portfolioErasure.erase(user)
        val erased = ownedAssetErasure.erase(user)
        log.info(
            "Deleted wealth data for user {}: {} portfolios, {} assets, {} brokers",
            user.id,
            portfolios,
            erased.assets,
            erased.brokers
        )
        return OffboardingResult(
            success = true,
            deletedCount = portfolios + erased.assets,
            type = "wealth",
            message = "$portfolios portfolios and ${erased.assets} assets deleted"
        )
    }

    /**
     * Delete entire user account including all data.
     * This is the nuclear option - deletes everything and the user record.
     */
    fun deleteUserAccount(): OffboardingResult {
        val user = activeUser()

        // Soft-delete: deactivate the user and clear its cash-portfolio link up
        // front. The user row SURVIVES offboarding (re-registration reactivates
        // it), so cash_portfolio_id — a NO-ACTION FK to portfolio — must be nulled
        // before the portfolios below are deleted or the surviving row dangles.
        systemUserRepository.deactivate(user.id)

        val portfolios = portfolioErasure.erase(user)
        val erased = ownedAssetErasure.erase(user)
        taxRateRepository.deleteAllByOwnerId(user.id)
        watchlistService.deleteByOwner(user.id)
        userPreferencesRepository.findByOwner(user).ifPresent {
            userPreferencesRepository.delete(it)
        }

        // User row already deactivated above (not deleted) — evict the cache
        // so the inactive state is re-read on the next request.
        systemUserCache.evictAll()

        log.info(
            "Deactivated account for user {}: deleted {} portfolios, {} assets, {} brokers",
            user.id,
            portfolios,
            erased.assets,
            erased.brokers
        )
        return OffboardingResult(
            success = true,
            deletedCount = 1,
            type = "account",
            message = "Account deactivated and all data deleted"
        )
    }

    private fun activeUser(): SystemUser =
        systemUserService.getActiveUser()
            ?: throw BusinessException(SystemUserService.USER_NOT_AUTHENTICATED)
}