package com.beancounter.marketdata.payslip

import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.KeyGenUtils
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.CheckConstraint
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.LocalDate

/**
 * A user's monthly payslip, scheduled from the first one they recorded. Each [ownerId]
 * (`SystemUser.id`) has at most one. [nextPayDate] is the next payslip to record; it falls on
 * [dayOfMonth], or the month's last day when the month is shorter (V56).
 */
@Entity
@Table(
    name = "recurring_payslip",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_recurring_payslip_owner", columnNames = ["owner_id"])
    ],
    check = [
        CheckConstraint(
            name = "ck_recurring_payslip_day_of_month",
            constraint = "\"day_of_month\" BETWEEN 1 AND 31"
        ),
        CheckConstraint(name = "ck_recurring_payslip_gross_salary", constraint = "\"gross_salary\" > 0"),
        CheckConstraint(name = "ck_recurring_payslip_tax", constraint = "\"tax\" >= 0")
    ]
)
data class RecurringPayslip(
    @Id
    val id: String = KeyGenUtils().id,
    @JsonIgnore
    @Column(nullable = false)
    val ownerId: String,
    @Column(nullable = false)
    val portfolioId: String,
    @Column(nullable = false)
    val cashAssetId: String,
    @Column(precision = 19, scale = 4, nullable = false)
    val grossSalary: BigDecimal,
    @Column(precision = 19, scale = 4, nullable = false)
    val tax: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false)
    val dayOfMonth: Int,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DateUtils.FORMAT)
    @Column(nullable = false)
    val nextPayDate: LocalDate
)