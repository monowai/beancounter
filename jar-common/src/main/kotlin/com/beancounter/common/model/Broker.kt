package com.beancounter.common.model

import com.beancounter.common.utils.KeyGenUtils
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * Broker/Custodian that holds assets and executes transactions.
 * Each user can define their own brokers for reporting purposes.
 */
@Entity
@Table(
    uniqueConstraints = [
        UniqueConstraint(name = "uk_broker_owner_name", columnNames = ["owner_id", "name"])
    ]
)
data class Broker(
    @Id
    var id: String = KeyGenUtils().id,
    val name: String,
    @ManyToOne
    val owner: SystemUser,
    val accountNumber: String? = null,
    val notes: String? = null
)