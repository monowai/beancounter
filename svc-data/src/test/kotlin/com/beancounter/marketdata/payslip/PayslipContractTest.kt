package com.beancounter.marketdata.payslip

import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * svc-data and svc-retire share no class for the recurring payslip messages: each side declares
 * its own. These are the sample documents from the agreed contract, verbatim, so a field rename
 * on this side fails here rather than on the broker.
 */
class PayslipContractTest {
    @Test
    fun `should read the payslip due sample document`() {
        val due = objectMapper.readValue(PAYSLIP_DUE, PayslipDue::class.java)

        assertThat(due).isEqualTo(
            PayslipDue(
                recurringPayslipId = "rp-1",
                ownerId = "su-1",
                portfolioId = "pf-1",
                payDate = LocalDate.of(2026, 12, 25),
                grossSalary = BigDecimal("10000.00"),
                tax = BigDecimal("500.00"),
                currency = "SGD",
                cashAssetId = "cash-1",
                cpfAssetId = "cpf-1",
                yearOfBirth = 1970,
                monthOfBirth = 6
            )
        )
    }

    @Test
    fun `should write a payslip due that reads back the same with absent optional fields as null`() {
        val due =
            PayslipDue(
                recurringPayslipId = "rp-1",
                ownerId = "su-1",
                portfolioId = "pf-1",
                payDate = LocalDate.of(2026, 12, 25),
                grossSalary = BigDecimal("10000.00"),
                tax = BigDecimal.ZERO,
                currency = "SGD",
                cashAssetId = "cash-1"
            )

        val json = objectMapper.readTree(objectMapper.writeValueAsString(due))

        assertThat(json.path("payDate").asString()).isEqualTo("2026-12-25")
        assertThat(json.has("cpfAssetId")).isTrue()
        assertThat(json.path("cpfAssetId").isNull).isTrue()
        assertThat(json.path("yearOfBirth").isNull).isTrue()
        assertThat(json.path("monthOfBirth").isNull).isTrue()
        assertThat(objectMapper.readValue(json.toString(), PayslipDue::class.java))
            .usingRecursiveComparison()
            .withComparatorForType(BigDecimal::compareTo, BigDecimal::class.java)
            .isEqualTo(due)
    }

    @Test
    fun `should read the payslip transactions sample document`() {
        val payslip = objectMapper.readValue(PAYSLIP_TRN, PayslipTrns::class.java)

        assertThat(payslip.recurringPayslipId).isEqualTo("rp-1")
        assertThat(payslip.portfolioId).isEqualTo("pf-1")
        assertThat(payslip.payDate).isEqualTo(LocalDate.of(2026, 12, 25))
        assertThat(payslip.trns.map { it.callerRef }).containsExactly(
            CallerRef("PAYSLIP", "rp-1", "2026-12-25-1"),
            CallerRef("PAYSLIP", "rp-1", "2026-12-25-2"),
            CallerRef("PAYSLIP", "rp-1", "2026-12-25-3"),
            CallerRef("PAYSLIP", "rp-1", "2026-12-25-4")
        )
        assertThat(payslip.trns.map { it.trnType })
            .containsExactly(TrnType.INCOME, TrnType.DEDUCTION, TrnType.DEDUCTION, TrnType.ADD)

        val income = payslip.trns[0]
        assertThat(income.assetId).isEqualTo("cash-1")
        assertThat(income.cashAssetId).isEqualTo("cash-1")
        assertThat(income.quantity).isEqualByComparingTo("10000.00")
        assertThat(income.price).isEqualByComparingTo("1")
        assertThat(income.tradeCurrency).isEqualTo("SGD")
        assertThat(income.cashCurrency).isEqualTo("SGD")
        assertThat(income.tradeAmount).isEqualByComparingTo("10000.00")
        assertThat(income.cashAmount).isEqualByComparingTo("10000.00")
        assertThat(income.tradeDate).isEqualTo(LocalDate.of(2026, 12, 25))
        assertThat(income.status).isEqualTo(TrnStatus.SETTLED)
        assertThat(income.comments).isEqualTo("Salary")

        val tax = payslip.trns[2]
        assertThat(tax.tax).isEqualByComparingTo("500.00")
        assertThat(tax.cashAmount).isEqualByComparingTo("-500.00")

        val cpf = payslip.trns[3]
        assertThat(cpf.assetId).isEqualTo("cpf-1")
        assertThat(cpf.cashAssetId).isNull()
        assertThat(cpf.cashAmount).isEqualByComparingTo("0")
        assertThat(cpf.subAccounts).containsOnlyKeys("OA", "SA", "MA")
        assertThat(cpf.subAccounts!!["OA"]).isEqualByComparingTo("2300.00")
        assertThat(cpf.subAccounts!!["SA"]).isEqualByComparingTo("600.00")
        assertThat(cpf.subAccounts!!["MA"]).isEqualByComparingTo("800.00")
    }

    companion object {
        // Verbatim from the svc-data <-> svc-retire payslip contract.
        const val PAYSLIP_DUE = """
{
  "recurringPayslipId": "rp-1",
  "ownerId": "su-1",
  "portfolioId": "pf-1",
  "payDate": "2026-12-25",
  "grossSalary": 10000.00,
  "tax": 500.00,
  "currency": "SGD",
  "cashAssetId": "cash-1",
  "cpfAssetId": "cpf-1",
  "yearOfBirth": 1970,
  "monthOfBirth": 6
}
"""

        const val PAYSLIP_TRN = """
{
  "recurringPayslipId": "rp-1",
  "portfolioId": "pf-1",
  "payDate": "2026-12-25",
  "trns": [
    {
      "callerRef": {"provider": "PAYSLIP", "batch": "rp-1", "callerId": "2026-12-25-1"},
      "assetId": "cash-1", "cashAssetId": "cash-1", "trnType": "INCOME",
      "quantity": 10000.00, "price": 1, "tradeCurrency": "SGD", "cashCurrency": "SGD",
      "tradeAmount": 10000.00, "cashAmount": 10000.00, "tradeDate": "2026-12-25",
      "fees": 0, "tax": 0, "status": "SETTLED", "comments": "Salary"
    },
    {
      "callerRef": {"provider": "PAYSLIP", "batch": "rp-1", "callerId": "2026-12-25-2"},
      "assetId": "cash-1", "cashAssetId": "cash-1", "trnType": "DEDUCTION",
      "quantity": 2000.00, "price": 1, "tradeCurrency": "SGD", "cashCurrency": "SGD",
      "tradeAmount": 2000.00, "cashAmount": -2000.00, "tradeDate": "2026-12-25",
      "fees": 0, "tax": 0, "status": "SETTLED", "comments": "Employee CPF contribution"
    },
    {
      "callerRef": {"provider": "PAYSLIP", "batch": "rp-1", "callerId": "2026-12-25-3"},
      "assetId": "cash-1", "cashAssetId": "cash-1", "trnType": "DEDUCTION",
      "quantity": 500.00, "price": 1, "tradeCurrency": "SGD", "cashCurrency": "SGD",
      "tradeAmount": 500.00, "cashAmount": -500.00, "tradeDate": "2026-12-25",
      "fees": 0, "tax": 500.00, "status": "SETTLED", "comments": "Tax"
    },
    {
      "callerRef": {"provider": "PAYSLIP", "batch": "rp-1", "callerId": "2026-12-25-4"},
      "assetId": "cpf-1", "trnType": "ADD",
      "quantity": 3700.00, "price": 1, "tradeCurrency": "SGD", "cashCurrency": "SGD",
      "tradeAmount": 3700.00, "cashAmount": 0, "tradeDate": "2026-12-25",
      "fees": 0, "tax": 0, "status": "SETTLED", "comments": "CPF contribution",
      "subAccounts": {"OA": 2300.00, "SA": 600.00, "MA": 800.00}
    }
  ]
}
"""
    }
}