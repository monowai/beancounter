package com.beancounter.marketdata.assets

import com.beancounter.common.exception.ForbiddenException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.cache.CacheInvalidationProducer
import com.beancounter.marketdata.registration.SystemUserService
import com.beancounter.marketdata.trn.TrnRepository
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service for managing private asset configurations.
 *
 * Handles CRUD operations for asset-level income/expense settings.
 * Ownership and shared-read checks are delegated to [AssetAccessControl].
 */
@Service
@Transactional
class PrivateAssetConfigService(
    private val configRepository: PrivateAssetConfigRepository,
    private val assetRepository: AssetRepository,
    private val systemUserService: SystemUserService,
    private val trnRepository: TrnRepository,
    private val accessControl: AssetAccessControl,
    private val cacheInvalidationProducer: CacheInvalidationProducer,
    private val dateUtils: DateUtils = DateUtils()
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Get all configs for assets owned by the current user, or — when
     * [systemUserId] is supplied AND the caller is a service account — the
     * configs for that specific [SystemUser]. Used by svc-retire to resolve
     * a shared plan's pension/policy configs without leaking the viewer's
     * data.
     *
     * @param excludePortfolioIds If provided, configs for assets that have transactions
     *   in any of the specified portfolios will be excluded from the results.
     * @param systemUserId If provided, return configs owned by this SystemUser.
     *   Requires a service-account caller; non-service callers receive a
     *   [ForbiddenException].
     */
    fun getMyConfigs(
        excludePortfolioIds: Set<String>? = null,
        systemUserId: String? = null
    ): PrivateAssetConfigsResponse {
        val user =
            if (systemUserId != null) {
                if (!systemUserService.isServiceAccount()) {
                    throw ForbiddenException("systemUserId parameter requires service authentication")
                }
                systemUserService.findById(systemUserId)
                    ?: return PrivateAssetConfigsResponse(emptyList())
            } else {
                systemUserService.getActiveUser()
                    ?: return PrivateAssetConfigsResponse(emptyList())
            }
        var configs = configRepository.findByUserId(user.id)

        if (!excludePortfolioIds.isNullOrEmpty()) {
            val excludedAssetIds =
                trnRepository.findDistinctAssetIdsByPortfolioIds(excludePortfolioIds).toSet()
            configs = configs.filter { it.assetId !in excludedAssetIds }
        }

        // Resolve asset names
        val assetNames =
            configs.associate { config ->
                val asset = assetRepository.findById(config.assetId).orElse(null)
                config.assetId to (asset?.name ?: config.assetId)
            }

        return PrivateAssetConfigsResponse(configs, assetNames)
    }

    /**
     * Get config for a specific asset.
     * Returns null if no config exists.
     *
     * Read access is allowed for either the asset owner OR a viewer of any
     * portfolio that holds transactions for the asset (active share). Writes
     * remain owner-only via [AssetAccessControl.verifyOwnership].
     */
    fun getConfig(assetId: String): PrivateAssetConfigResponse? {
        accessControl.verifyReadAccess(assetId)
        val config = configRepository.findById(assetId).orElse(null) ?: return null
        return PrivateAssetConfigResponse(config)
    }

    /**
     * Save (create or update) a config for an asset.
     */
    fun saveConfig(
        assetId: String,
        request: PrivateAssetConfigRequest
    ): PrivateAssetConfigResponse {
        accessControl.verifyOwnership(assetId)
        logSaveRequest(assetId, request)
        val update = PrivateAssetConfigUpdate(request).also { it.validate() }

        // A new config starts from the entity defaults and takes the request like an update.
        val existing =
            configRepository.findById(assetId).orElse(null)
                ?: PrivateAssetConfig(assetId = assetId, createdDate = dateUtils.date)
        val config = update.applyTo(existing, assetId, dateUtils.date)

        val saved = configRepository.save(config)
        logSavedConfig(assetId, saved)
        // Contribution config feeds BALANCE contributions stamped onto trns at read time,
        // which svc-position's TWR consumes — its cached snapshots are now stale.
        cacheInvalidationProducer.sendAssetEvent(assetId)
        return PrivateAssetConfigResponse(saved)
    }

    private fun logSaveRequest(
        assetId: String,
        request: PrivateAssetConfigRequest
    ) {
        log.info(
            "Saving config for asset: $assetId - " +
                "request.isPension=${request.isPension}, " +
                "request.payoutAge=${request.payoutAge}, " +
                "request.lumpSum=${request.lumpSum}, " +
                "request.policyType=${request.policyType}"
        )
    }

    private fun logSavedConfig(
        assetId: String,
        saved: PrivateAssetConfig
    ) {
        log.info(
            "Saved config for asset: $assetId, " +
                "rental: ${saved.monthlyRentalIncome} ${saved.rentalCurrency}, " +
                "mgmtFee: ${saved.monthlyManagementFee}, " +
                "isPension: ${saved.isPension}, " +
                "payoutAge: ${saved.payoutAge}, " +
                "lumpSum: ${saved.lumpSum}, " +
                "policyType: ${saved.policyType}, " +
                "subAccounts: ${saved.subAccounts.size}"
        )
    }

    /**
     * Delete config for an asset.
     */
    fun deleteConfig(assetId: String) {
        accessControl.verifyOwnership(assetId)
        if (!configRepository.existsById(assetId)) {
            throw NotFoundException("Config not found for asset: $assetId")
        }
        configRepository.deleteById(assetId)
        log.info("Deleted config for asset: $assetId")
        cacheInvalidationProducer.sendAssetEvent(assetId)
    }

    /**
     * Get configs for multiple assets (for bulk operations).
     */
    fun getConfigsForAssets(assetIds: Collection<String>): PrivateAssetConfigsResponse {
        val configs = configRepository.findByAssetIdIn(assetIds)
        return PrivateAssetConfigsResponse(configs)
    }
}