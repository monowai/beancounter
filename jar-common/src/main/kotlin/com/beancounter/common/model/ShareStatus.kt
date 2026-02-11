package com.beancounter.common.model

/**
 * Status of a portfolio sharing relationship.
 */
enum class ShareStatus {
    PENDING_CLIENT_INVITE,
    PENDING_ADVISER_REQUEST,
    ACTIVE,
    REVOKED
}