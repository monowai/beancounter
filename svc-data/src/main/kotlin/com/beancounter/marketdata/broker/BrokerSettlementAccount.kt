package com.beancounter.marketdata.broker

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Broker
import com.beancounter.common.utils.KeyGenUtils
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * Maps a currency to a default settlement account for a broker.
 * Each broker can have one settlement account per currency.
 */
@Entity
@Table(
    name = "broker_settlement_account",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_broker_currency",
            columnNames = ["broker_id", "currency_code"]
        )
    ]
)
data class BrokerSettlementAccount(
    @Id
    val id: String = KeyGenUtils().id,
    @ManyToOne
    @JoinColumn(name = "broker_id", nullable = false)
    val broker: Broker,
    val currencyCode: String,
    @ManyToOne
    @JoinColumn(name = "account_id", nullable = false)
    val account: Asset
)