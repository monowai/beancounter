package com.beancounter.marketdata.milestone

import com.beancounter.common.model.MilestoneMode
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.UserExplorerAction
import com.beancounter.common.model.UserMilestone
import com.beancounter.marketdata.registration.UserPreferencesService
import jakarta.transaction.Transactional
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Service for managing user milestones and explorer actions.
 */
@Service
@Transactional
class MilestoneService(
    private val milestoneRepository: UserMilestoneRepository,
    private val explorerActionRepository: UserExplorerActionRepository,
    private val userPreferencesService: UserPreferencesService
) {
    fun getEarnedMilestones(owner: SystemUser): List<UserMilestone> = milestoneRepository.findByOwner(owner)

    /**
     * Record a milestone. Only upgrades tier, never downgrades.
     */
    fun earnMilestone(
        owner: SystemUser,
        milestoneId: String,
        tier: Int
    ): UserMilestone {
        val existing = milestoneRepository.findByOwnerAndMilestoneId(owner, milestoneId)
        if (existing.isPresent) {
            val milestone = existing.get()
            if (tier > milestone.tier) {
                milestone.tier = tier
                milestone.earnedAt = LocalDate.now()
                return milestoneRepository.save(milestone)
            }
            return milestone
        }
        return try {
            milestoneRepository.save(
                UserMilestone(
                    owner = owner,
                    milestoneId = milestoneId,
                    tier = tier
                )
            )
        } catch (_: DataIntegrityViolationException) {
            val raced =
                milestoneRepository
                    .findByOwnerAndMilestoneId(owner, milestoneId)
                    .orElseThrow {
                        IllegalStateException("Failed to earn milestone $milestoneId for ${owner.id}")
                    }
            if (tier > raced.tier) {
                raced.tier = tier
                raced.earnedAt = LocalDate.now()
                return milestoneRepository.save(raced)
            }
            raced
        }
    }

    fun getExplorerActions(owner: SystemUser): List<UserExplorerAction> = explorerActionRepository.findByOwner(owner)

    /**
     * Record an explorer action. Idempotent — duplicate inserts are ignored.
     */
    fun recordExplorerAction(
        owner: SystemUser,
        actionId: String
    ): UserExplorerAction =
        try {
            explorerActionRepository.save(
                UserExplorerAction(
                    owner = owner,
                    actionId = actionId
                )
            )
        } catch (_: DataIntegrityViolationException) {
            explorerActionRepository.findByOwner(owner).firstOrNull { it.actionId == actionId }
                ?: throw IllegalStateException("Failed to record explorer action $actionId for ${owner.id}")
        }

    fun getMilestoneMode(owner: SystemUser): MilestoneMode = userPreferencesService.getOrCreate(owner).milestoneMode
}