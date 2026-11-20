package com.beancounter.marketdata.payslip

import com.beancounter.common.input.TrnInput
import com.fasterxml.jackson.annotation.JsonFormat
import java.math.BigDecimal
import java.time.LocalDate

/**
 * PUT /payslips/recurring body. [payDate] is the payslip the browser has just recorded; the
 * next one is scheduled a month later.
 */
data class RecurringPayslipRequest(
    val portfolioId: String,
    val cashAssetId: String,
    val grossSalary: BigDecimal,
    val tax: BigDecimal = BigDecimal.ZERO,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val payDate: LocalDate
)

/** The caller's recurring payslip; [data] is null when there is none. */
data class RecurringPayslipResponse(
    val data: RecurringPayslip?
)

/*
 * Messages exchanged with svc-retire. svc-retire consumes beancounter jars at a released
 * version, so neither side shares a class: each declares its own, matching the agreed JSON
 * (see PayslipContractTest for the sample documents).
 */

/** svc-data -> svc-retire on `payslipDue-out-0`: a recurring payslip whose pay date has arrived. */
data class PayslipDue(
    val recurringPayslipId: String,
    // SystemUser.id
    val ownerId: String,
    val portfolioId: String,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val payDate: LocalDate,
    val grossSalary: BigDecimal,
    val tax: BigDecimal,
    // The cash asset's currency
    val currency: String,
    val cashAssetId: String,
    val cpfAssetId: String? = null,
    val yearOfBirth: Int? = null,
    val monthOfBirth: Int? = null
)

/** svc-retire -> svc-data on `payslipTrnConsumer-in-0`: the transactions for one payslip. */
data class PayslipTrns(
    val recurringPayslipId: String,
    val portfolioId: String,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val payDate: LocalDate,
    val trns: List<TrnInput> = emptyList()
)