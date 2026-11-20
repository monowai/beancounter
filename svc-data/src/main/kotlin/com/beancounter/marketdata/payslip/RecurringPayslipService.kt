package com.beancounter.marketdata.payslip

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.assets.AssetRepository
import com.beancounter.marketdata.portfolio.PortfolioRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * The owner's one recurring payslip: read, schedule (upsert) and stop. svc-data only schedules;
 * svc-retire calculates each payslip's transactions.
 */
@Service
@Transactional
class RecurringPayslipService(
    private val recurringPayslipRepository: RecurringPayslipRepository,
    private val portfolioRepository: PortfolioRepository,
    private val assetRepository: AssetRepository
) {
    fun get(owner: SystemUser): RecurringPayslip? = recurringPayslipRepository.findByOwnerId(owner.id).orElse(null)

    /**
     * Schedules the next payslip on [RecurringPayslipRequest.nextPayDate], replacing the owner's
     * row when there is one. Throws [BusinessException] (400) for a non-positive gross, a
     * negative tax, a day of month outside 1-31, a next pay date not on that day (or the
     * shorter month's last day), a portfolio the owner does not own or a cash asset that does
     * not exist or is another user's.
     */
    fun schedule(
        owner: SystemUser,
        request: RecurringPayslipRequest
    ): RecurringPayslip {
        validate(owner, request)
        val scheduled =
            RecurringPayslip(
                ownerId = owner.id,
                portfolioId = request.portfolioId,
                cashAssetId = request.cashAssetId,
                grossSalary = request.grossSalary,
                tax = request.tax,
                dayOfMonth = request.dayOfMonth,
                nextPayDate = request.nextPayDate
            )
        val existing = get(owner)
        return recurringPayslipRepository.save(
            if (existing == null) scheduled else scheduled.copy(id = existing.id)
        )
    }

    fun stop(owner: SystemUser) {
        recurringPayslipRepository.deleteByOwnerId(owner.id)
    }

    private fun validate(
        owner: SystemUser,
        request: RecurringPayslipRequest
    ) {
        val problem =
            when {
                request.grossSalary <= BigDecimal.ZERO -> {
                    "Gross salary must be greater than zero"
                }
                request.tax < BigDecimal.ZERO -> {
                    "Tax cannot be negative"
                }
                request.dayOfMonth !in PayDates.DAYS -> {
                    "Day of month must be between 1 and 31"
                }
                !PayDates.fallsOn(request.nextPayDate, request.dayOfMonth) -> {
                    "Next pay date ${request.nextPayDate} is not on day ${request.dayOfMonth}"
                }
                !ownsPortfolio(owner, request.portfolioId) -> {
                    "Portfolio ${request.portfolioId} is not one of yours"
                }
                !canUseCash(owner, request.cashAssetId) -> {
                    "Cash asset ${request.cashAssetId} does not exist"
                }
                else -> {
                    null
                }
            }
        if (problem != null) {
            throw BusinessException(problem)
        }
    }

    /**
     * A shared cash balance (no owner) or one of the owner's own accounts. Another user's
     * account answers the same as a missing one, so its id is not confirmed to exist.
     */
    private fun canUseCash(
        owner: SystemUser,
        cashAssetId: String
    ): Boolean {
        val asset = assetRepository.findById(cashAssetId).orElse(null) ?: return false
        val assetOwner = asset.systemUser ?: return true
        return assetOwner.id == owner.id
    }

    private fun ownsPortfolio(
        owner: SystemUser,
        portfolioId: String
    ): Boolean =
        portfolioRepository
            .findById(portfolioId)
            .orElse(null)
            ?.owner
            ?.id == owner.id
}