package com.beancounter.common.contracts

/**
 * TrnId's that were deleted.
 *
 * [siblings] are auto-settled transactions (typically the WITHDRAWAL +
 * DEPOSIT cash legs emitted alongside the parent trade) that share the
 * parent's group key. The UI prompts the user before deleting them; the
 * server does NOT auto-cascade.
 */
data class TrnDeleteResponse(
    override val data: Collection<String> = arrayListOf(),
    val siblings: List<String> = emptyList()
) : Payload<Collection<String>>