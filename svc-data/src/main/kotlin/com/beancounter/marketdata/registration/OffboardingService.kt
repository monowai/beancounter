package com.beancounter.marketdata.registration

import com.beancounter.common.exception.BusinessException
import com.beancounter.marketdata.assets.AssetRepository
import com.beancounter.marketdata.portfolio.PortfolioRepository
import com.beancounter.marketdata.providers.MarketDataRepo
import com.beancounter.marketdata.tax.TaxRateRepository
import com.beancounter.marketdata.trn.TrnRepository
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service for user offboarding - allows users to remove their data from Beancounter.
 *
 * Deletion order respects FK constraints:
 * 1. Transactions (via portfolio and asset)
 * 2. MarketData (via asset)
 * 3. Portfolios
 * 4. Assets
 * 5. TaxRates
 * 6. UserPreferences
 * 7. SystemUser
 */
@Service
@Transactional
class OffboardingService(
    private val systemUserService: SystemUserService,
    private val systemUserRepository: SystemUserRepository,
    private val systemUserCache: SystemUserCache,
    private val portfolioRepository: PortfolioRepository,
    private val assetRepository: AssetRepository,
    private val trnRepository: TrnRepository,
    private val marketDataRepo: MarketDataRepo,
    private val taxRateRepository: TaxRateRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Get summary of user's data for offboarding preview.
     */
    fun getSummary(): OffboardingSummaryResponse {
        val user =
            systemUserService.getActiveUser()
                ?: throw BusinessException("User not authenticated")

        val portfolios = portfolioRepository.findByOwner(user).toList()
        val assets = assetRepository.findBySystemUserId(user.id)
        val taxRates = taxRateRepository.findAllByOwnerId(user.id)

        return OffboardingSummaryResponse(
            portfolioCount = portfolios.size,
            assetCount = assets.size,
            taxRateCount = taxRates.size
        )
    }

    /**
     * Delete all user-owned assets and their associated data.
     * Cascades to: transactions, market data
     */
    fun deleteUserAssets(): OffboardingResult {
        val user =
            systemUserService.getActiveUser()
                ?: throw BusinessException("User not authenticated")

        val assets = assetRepository.findBySystemUserId(user.id)
        if (assets.isEmpty()) {
            return OffboardingResult(
                success = true,
                deletedCount = 0,
                type = "assets",
                message = "No assets to delete"
            )
        }

        var deletedCount = 0
        assets.forEach { asset ->
            trnRepository.deleteByAssetId(asset.id)
            marketDataRepo.deleteByAssetId(asset.id)
            assetRepository.delete(asset)
            deletedCount++
        }

        log.info("Deleted {} assets for user {}", deletedCount, user.id)
        return OffboardingResult(
            success = true,
            deletedCount = deletedCount,
            type = "assets"
        )
    }

    /**
     * Delete all user's portfolios and their transactions.
     */
    fun deleteUserPortfolios(): OffboardingResult {
        val user =
            systemUserService.getActiveUser()
                ?: throw BusinessException("User not authenticated")

        val portfolios = portfolioRepository.findByOwner(user).toList()
        if (portfolios.isEmpty()) {
            return OffboardingResult(
                success = true,
                deletedCount = 0,
                type = "portfolios",
                message = "No portfolios to delete"
            )
        }

        var deletedCount = 0
        portfolios.forEach { portfolio ->
            trnRepository.deleteByPortfolioId(portfolio.id)
            portfolioRepository.delete(portfolio)
            deletedCount++
        }

        log.info("Deleted {} portfolios for user {}", deletedCount, user.id)
        return OffboardingResult(
            success = true,
            deletedCount = deletedCount,
            type = "portfolios"
        )
    }

    /**
     * Delete all user's wealth data: portfolios, assets, and their transactions.
     * This is a combined operation for the Wealth step in offboarding.
     */
    fun deleteUserWealth(): OffboardingResult {
        val user =
            systemUserService.getActiveUser()
                ?: throw BusinessException("User not authenticated")

        var totalDeleted = 0

        // 1. Delete portfolios and their transactions
        val portfolios = portfolioRepository.findByOwner(user).toList()
        portfolios.forEach { portfolio ->
            trnRepository.deleteByPortfolioId(portfolio.id)
            portfolioRepository.delete(portfolio)
            totalDeleted++
        }

        // 2. Delete assets and their data
        val assets = assetRepository.findBySystemUserId(user.id)
        assets.forEach { asset ->
            trnRepository.deleteByAssetId(asset.id)
            marketDataRepo.deleteByAssetId(asset.id)
            assetRepository.delete(asset)
            totalDeleted++
        }

        log.info(
            "Deleted wealth data for user {}: {} portfolios, {} assets",
            user.id,
            portfolios.size,
            assets.size
        )

        return OffboardingResult(
            success = true,
            deletedCount = totalDeleted,
            type = "wealth",
            message = "${portfolios.size} portfolios and ${assets.size} assets deleted"
        )
    }

    /**
     * Delete entire user account including all data.
     * This is the nuclear option - deletes everything and the user record.
     */
    fun deleteUserAccount(): OffboardingResult {
        val user =
            systemUserService.getActiveUser()
                ?: throw BusinessException("User not authenticated")

        // Delete in order of FK dependencies
        // 1. Delete portfolios and their transactions
        val portfolios = portfolioRepository.findByOwner(user).toList()
        portfolios.forEach { portfolio ->
            trnRepository.deleteByPortfolioId(portfolio.id)
            portfolioRepository.delete(portfolio)
        }

        // 2. Delete assets and their data
        val assets = assetRepository.findBySystemUserId(user.id)
        assets.forEach { asset ->
            trnRepository.deleteByAssetId(asset.id)
            marketDataRepo.deleteByAssetId(asset.id)
            assetRepository.delete(asset)
        }

        // 3. Delete tax rates
        taxRateRepository.deleteAllByOwnerId(user.id)

        // 4. Delete user preferences
        userPreferencesRepository.findByOwner(user).ifPresent {
            userPreferencesRepository.delete(it)
        }

        // 5. Delete the user record
        systemUserRepository.delete(user)

        // 6. Evict from cache
        systemUserCache.evictAll()

        log.info(
            "Deleted account for user {}: {} portfolios, {} assets",
            user.id,
            portfolios.size,
            assets.size
        )

        return OffboardingResult(
            success = true,
            deletedCount = 1,
            type = "account",
            message = "Account and all data deleted"
        )
    }
}