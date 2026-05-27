package com.beancounter.common.contracts

import com.beancounter.common.model.TrnStatus

/**
 * Request to update a single transaction's [TrnStatus] (e.g.
 * SETTLED → PROPOSED for an Unsettle action).
 */
data class TrnStatusUpdateRequest(
    val status: TrnStatus
)