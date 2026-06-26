package com.beancounter.marketdata.registration

import com.beancounter.common.exception.BusinessException
import com.beancounter.marketdata.assets.AssetRepository
import com.beancounter.marketdata.broker.BrokerRepository
import com.beancounter.marketdata.broker.BrokerSettlementAccountRepository
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
    private val brokerRepository: BrokerRepository,
    private val brokerSettlementAccountRepository: BrokerSettlementAccountRepository,
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
                ?: throw BusinessException(SystemUserService.USER_NOT_AUTHENTICATED)

        val portfolios = portfolioRepository.findByOwner(user).toList()
        val assets = assetRepository.findBySystemUserId(user.id)
        val taxRates = taxRateRepository.findAllByOwnerId(user.id)
        val brokers = brokerRepository.findByOwner(user)

        return OffboardingSummaryResponse(
            portfolioCount = portfolios.size,
            assetCount = assets.size,
            taxRateCount = taxRates.size,
            brokerCount = brokers.size
        )
    }

    /**
     * Delete all user-owned assets and their associated data.
     * Cascades to: transactions, market data
     */
    fun deleteUserAssets(): OffboardingResult {
        val user =
            systemUserService.getActiveUser()
                ?: throw BusinessException(SystemUserService.USER_NOT_AUTHENTICATED)

        val assets = assetRepository.findBySystemUserId(user.id)
        if (assets.isEmpty()) {
            return OffboardingResult(
                success = true,
                deletedCount = 0,
                type = "assets",
                message = "No assets to delete"
            )
        }

        // Clear settlement accounts before assets: broker_settlement_account has a NO-ACTION
        // FK to the cash asset (fk_settlement_account), so the asset delete below trips that
        // constraint unless the settlement rows are gone first.
        brokerSettlementAccountRepository.deleteByBrokerOwnerId(user.id)

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
                ?: throw BusinessException(SystemUserService.USER_NOT_AUTHENTICATED)

        val portfolios = portfolioRepository.findByOwner(user).toList()
        if (portfolios.isEmpty()) {
            return OffboardingResult(
                success = true,
                deletedCount = 0,
                type = "portfolios",
                message = "No portfolios to delete"
            )
        }

        portfolios.forEach { portfolio ->
            trnRepository.deleteByPortfolioId(portfolio.id)
        }
        portfolioRepository.deleteByOwnerId(user.id)

        log.info("Deleted {} portfolios for user {}", portfolios.size, user.id)
        return OffboardingResult(
            success = true,
            deletedCount = portfolios.size,
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
                ?: throw BusinessException(SystemUserService.USER_NOT_AUTHENTICATED)

        var totalDeleted = 0

        // 1. Delete portfolios and their transactions
        val portfolios = portfolioRepository.findByOwner(user).toList()
        portfolios.forEach { portfolio ->
            trnRepository.deleteByPortfolioId(portfolio.id)
        }
        portfolioRepository.deleteByOwnerId(user.id)
        totalDeleted += portfolios.size

        // 2. Clear settlement accounts BEFORE assets. broker_settlement_account has a NO-ACTION
        //    FK to the cash asset (fk_settlement_account); the asset delete below rolls back the
        //    whole wealth delete (kauri 409) unless the settlement rows are cleared first.
        brokerSettlementAccountRepository.deleteByBrokerOwnerId(user.id)

        // 3. Delete assets and their data
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
                ?: throw BusinessException(SystemUserService.USER_NOT_AUTHENTICATED)

        // Soft-delete: deactivate the user and clear its cash-portfolio link up
        // front. The user row SURVIVES offboarding (re-registration reactivates
        // it), so cash_portfolio_id — a NO-ACTION FK to portfolio — must be nulled
        // before the portfolios below are deleted or the surviving row dangles.
        systemUserRepository.deactivate(user.id)

        // Delete data in order of FK dependencies
        // 1. Delete portfolios and their transactions
        val portfolios = portfolioRepository.findByOwner(user).toList()
        portfolios.forEach { portfolio ->
            trnRepository.deleteByPortfolioId(portfolio.id)
        }
        portfolioRepository.deleteByOwnerId(user.id)

        // 2. Clear settlement accounts BEFORE assets and brokers.
        //    broker_settlement_account has NO-ACTION FKs to BOTH the cash asset
        //    (fk_settlement_account) and the broker (fk_settlement_broker), so the
        //    asset and broker bulk deletes below trip those constraints unless the
        //    settlement rows are gone first.
        brokerSettlementAccountRepository.deleteByBrokerOwnerId(user.id)

        // 3. Delete assets and their data
        val assets = assetRepository.findBySystemUserId(user.id)
        assets.forEach { asset ->
            trnRepository.deleteByAssetId(asset.id)
            marketDataRepo.deleteByAssetId(asset.id)
            assetRepository.delete(asset)
        }

        // 4. Delete brokers — safe AFTER trns (trn.broker_id FK) and settlement
        //    accounts are gone. Idempotent bulk delete.
        val brokerCount = brokerRepository.deleteByOwnerId(user.id)

        // 4. Delete tax rates
        taxRateRepository.deleteAllByOwnerId(user.id)

        // 5. Delete user preferences
        userPreferencesRepository.findByOwner(user).ifPresent {
            userPreferencesRepository.delete(it)
        }

        // 6. User row already deactivated above (not deleted) — evict the cache
        //    so the inactive state is re-read on the next request.
        systemUserCache.evictAll()

        log.info(
            "Deactivated account for user {}: deleted {} portfolios, {} assets, {} brokers",
            user.id,
            portfolios.size,
            assets.size,
            brokerCount
        )

        return OffboardingResult(
            success = true,
            deletedCount = 1,
            type = "account",
            message = "Account deactivated and all data deleted"
        )
    }
}