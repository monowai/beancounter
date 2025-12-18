package com.beancounter.common.contracts

import com.beancounter.common.model.HoldingsView

/**
 * Request to update user preferences.
 * All fields are optional - only provided fields will be updated.
 */
data class UserPreferencesRequest(
    val preferredName: String? = null,
    val defaultHoldingsView: HoldingsView? = null,
    val baseCurrencyCode: String? = null
)