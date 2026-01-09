package com.beancounter.common.contracts

import com.beancounter.common.model.GroupByPreference
import com.beancounter.common.model.HoldingsView
import com.beancounter.common.model.ValueInPreference

/**
 * Request to update user preferences.
 * All fields are optional - only provided fields will be updated.
 */
data class UserPreferencesRequest(
    val preferredName: String? = null,
    val defaultHoldingsView: HoldingsView? = null,
    val defaultValueIn: ValueInPreference? = null,
    val defaultGroupBy: GroupByPreference? = null,
    val baseCurrencyCode: String? = null,
    val reportingCurrencyCode: String? = null,
    val showWeightedIrr: Boolean? = null
)