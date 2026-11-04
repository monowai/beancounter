package com.beancounter.common.model

import java.time.Instant

/**
 * A sharing relationship between the owner of something and another user, whatever is shared.
 * [PortfolioShare] and [ResourceShare] follow one lifecycle: an owner's invite or an adviser's
 * request stays pending until the invited party accepts it, and any party can revoke it.
 */
interface Share {
    val id: String
    val sharedWith: SystemUser
    val createdBy: SystemUser
    val targetUser: SystemUser?
    var status: ShareStatus
    var acceptedAt: Instant?

    /**
     * Who owns what is shared; null while an adviser's request names nothing yet. A function,
     * not a property, so it adds nothing to the share's JSON.
     */
    fun owner(): SystemUser?
}