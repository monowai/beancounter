package com.beancounter.common.contracts

import com.beancounter.common.model.Trn

/**
 * Response for a single-trn status update (e.g. Unsettle action).
 *
 * [siblings] are auto-settled transactions linked to the parent by
 * `callerRef.batch == parent.callerRef.callerId AND provider == BC-AUTO`.
 * The UI uses this list to prompt the user to delete the cash legs after
 * an Unsettle. Empty when the parent has no auto-emitted children.
 */
data class TrnStatusUpdateResponse(
    val updated: Trn,
    val siblings: List<String> = emptyList()
)