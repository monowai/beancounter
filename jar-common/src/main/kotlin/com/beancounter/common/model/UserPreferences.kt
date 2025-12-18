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
 * Stores settings like default holdings view and base currency.
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
    var defaultHoldingsView: HoldingsView = HoldingsView.SUMMARY,
    var baseCurrencyCode: String = "USD"
)