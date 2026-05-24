package com.beancounter.common.model

import com.beancounter.common.utils.KeyGenUtils
import jakarta.persistence.Column
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
    var showWeightedIrr: Boolean = true,
    var enableTwr: Boolean = false,
    @Enumerated(EnumType.STRING)
    @Column(name = "milestone_mode")
    var milestoneMode: MilestoneMode = MilestoneMode.ACTIVE,
    @Column(name = "auto_settle")
    var autoSettle: Boolean = true,
    // Profile demographics — mirrored from svc-retire's UserIndependenceSettings
    // so screens that live in the svc-data scope (Edit Asset, holdings views)
    // can resolve the user's current age without a runtime dependency on
    // svc-retire. svc-retire still owns the write of these fields; this is
    // a denormalised read copy.
    @Column(name = "year_of_birth")
    var yearOfBirth: Int? = null,
    @Column(name = "month_of_birth")
    var monthOfBirth: Int? = null,
    @Column(name = "life_expectancy")
    var lifeExpectancy: Int? = null
)