package com.beancounter.marketdata.milestone

import com.beancounter.common.model.MilestoneMode
import com.beancounter.common.model.UserMilestone

/**
 * API response containing all earned milestones, explorer actions, and notification mode.
 */
data class MilestonesResponse(
    val earned: List<UserMilestone>,
    val explorerActions: List<String>,
    val mode: MilestoneMode
)

/**
 * Request to earn a milestone.
 */
data class EarnMilestoneRequest(
    val milestoneId: String,
    val tier: Int
)

/**
 * Request to record an explorer action.
 */
data class ExplorerActionRequest(
    val actionId: String
)