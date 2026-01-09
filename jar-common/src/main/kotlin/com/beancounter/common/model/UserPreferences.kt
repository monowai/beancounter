package com.beancounter.common.model

import com.beancounter.common.utils.KeyGenUtils
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table

/**
 * User preferences for the application.
 * Stores settings like default holdings view, value currency, grouping, and currency defaults.
 *
 * Currency fields:
 * - baseCurrencyCode: System base currency for cost tracking and portfolio jurisdiction defaults
 * - reportingCurrencyCode: Default currency for displaying portfolio values and reports
 */
@Entity
@Table
data class UserPreferences(
    @Id
    val id: String = KeyGenUtils().id,
    @OneToOne
    @JoinColumn(name = "owner_id", unique = true)
    val owner: SystemUser,
    var preferredName: String? = null,
    @Enumerated(EnumType.STRING)
    var defaultHoldingsView: HoldingsView = HoldingsView.CARDS,
    @Enumerated(EnumType.STRING)
    var defaultValueIn: ValueInPreference = ValueInPreference.PORTFOLIO,
    @Enumerated(EnumType.STRING)
    var defaultGroupBy: GroupByPreference = GroupByPreference.ASSET_CLASS,
    var baseCurrencyCode: String = "USD",
    var reportingCurrencyCode: String = "USD",
    var showWeightedIrr: Boolean = true
)