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
    // Profile demographics. svc-data is the master record — onboarding writes
    // these directly here (PATCH /api/me), and svc-retire reads them as the
    // user's account-wide default when its own per-plan profile has no
    // override yet. See bc-claude/USER_PROFILE.md.
    @Column(name = "year_of_birth")
    var yearOfBirth: Int? = null,
    @Column(name = "month_of_birth")
    var monthOfBirth: Int? = null,
    @Column(name = "target_independence_age")
    var targetIndependenceAge: Int? = null,
    @Column(name = "life_expectancy")
    var lifeExpectancy: Int? = null,
    // Defaults remembered by the bc-view "Enter Payslip" feature so the
    // user's preferred target portfolio and cash asset are pre-selected.
    @Column(name = "default_payslip_portfolio_id")
    var defaultPayslipPortfolioId: String? = null,
    @Column(name = "default_payslip_cash_asset_id")
    var defaultPayslipCashAssetId: String? = null
)