package com.beancounter.marketdata.payslip

import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.assets.AssetRepository
import com.beancounter.marketdata.assets.privateasset.PolicyType
import com.beancounter.marketdata.assets.privateasset.PrivateAssetConfigRepository
import com.beancounter.marketdata.registration.UserPreferencesRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Finds the recurring payslips whose next pay date has arrived and publishes one [PayslipDue]
 * for each, for that pay date only: a row several months behind catches up a month per run.
 * Does not advance the row; [PayslipRecorder] does that once the transactions are recorded.
 */
@Service
class PayslipDueService(
    private val recurringPayslipRepository: RecurringPayslipRepository,
    private val assetRepository: AssetRepository,
    private val privateAssetConfigRepository: PrivateAssetConfigRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val payslipDueProducer: PayslipDueProducer,
    private val dateUtils: DateUtils
) {
    /** Returns the number of due messages published. */
    fun publishDue(): Int {
        val due = recurringPayslipRepository.findByNextPayDateLessThanEqual(dateUtils.date)
        var published = 0
        for (payslip in due) {
            val message = toDue(payslip) ?: continue
            payslipDueProducer.send(message)
            published++
        }
        return published
    }

    private fun toDue(payslip: RecurringPayslip): PayslipDue? {
        val cash = assetRepository.findById(payslip.cashAssetId).orElse(null)
        if (cash == null) {
            log.warn("Recurring payslip {}: cash asset {} not found, skipped", payslip.id, payslip.cashAssetId)
            return null
        }
        val currency = cash.accountingType?.currency?.code ?: cash.market.currency.code
        val preferences = userPreferencesRepository.findByOwnerId(payslip.ownerId).orElse(null)
        return PayslipDue(
            recurringPayslipId = payslip.id,
            ownerId = payslip.ownerId,
            portfolioId = payslip.portfolioId,
            payDate = payslip.nextPayDate,
            grossSalary = payslip.grossSalary,
            tax = payslip.tax,
            currency = currency,
            cashAssetId = payslip.cashAssetId,
            cpfAssetId = cpfAssetId(payslip.ownerId),
            yearOfBirth = preferences?.yearOfBirth,
            monthOfBirth = preferences?.monthOfBirth
        )
    }

    /** The owner's private asset whose policy type is CPF, if any. */
    private fun cpfAssetId(ownerId: String): String? =
        privateAssetConfigRepository
            .findByUserId(ownerId)
            .firstOrNull { it.policyType == PolicyType.CPF }
            ?.assetId

    companion object {
        private val log = LoggerFactory.getLogger(PayslipDueService::class.java)
    }
}