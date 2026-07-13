package com.beancounter.marketdata.trn

import com.beancounter.common.model.Trn
import com.beancounter.marketdata.portfolio.PortfolioAccessControl
import com.beancounter.marketdata.registration.SystemUserService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Cross-cutting read-time processing applied to every Trn collection returned by
 * the trn services: version upgrade (persisting the migrated row), optional
 * owner-scoped security filtering, and BALANCE contribution stamping.
 *
 * Asset hydration itself happens via AssetEntityListener @PostLoad — Trn.asset
 * and Trn.cashAsset arrive populated from JPA; this only runs the migrate +
 * stamp steps.
 */
@Service
@Transactional
class TrnPostProcessor(
    private val trnMigrator: TrnMigrator,
    private val trnRepository: TrnRepository,
    private val balanceContributionStamper: BalanceContributionStamper,
    private val systemUserService: SystemUserService,
    private val portfolioAccessControl: PortfolioAccessControl
) {
    private val log = LoggerFactory.getLogger(TrnPostProcessor::class.java)

    fun postProcess(trns: List<Trn>): List<Trn> {
        log.trace("PostProcess ${trns.size} transactions")
        // Version-upgrade step; asset references are already hydrated by JPA.
        for (trn in trns) {
            val upgraded = trnMigrator.upgrade(trn)
            if (upgraded.version != trn.version) {
                trnRepository.save(upgraded)
            }
        }
        // Read-time only: stamp contribution on BALANCE snapshots so
        // svc-position can separate interest from fresh principal.
        balanceContributionStamper.stamp(trns)
        log.trace("Completed postProcess trns: ${trns.size}")
        return trns
    }

    fun postProcess(
        trns: Iterable<Trn>,
        secure: Boolean = true
    ): Collection<Trn> {
        if (secure) {
            val systemUser = systemUserService.getOrThrow()
            val filteredTrns =
                trns.filter {
                    portfolioAccessControl.isViewable(systemUser, it.portfolio)
                }
            return postProcess(filteredTrns)
        } else {
            return postProcess(trns.toList())
        }
    }
}