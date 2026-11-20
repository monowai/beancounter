package com.beancounter.marketdata.payslip

import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.marketdata.portfolio.PortfolioRepository
import com.beancounter.marketdata.trn.TrnService
import com.beancounter.marketdata.trn.io.TrnImportService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Records the transactions svc-retire calculated for a due payslip, then moves the recurring
 * payslip on to its next pay date, in one transaction. A message for a stopped payslip, for a
 * pay date other than the next one or for another portfolio is dropped. When the legs are
 * already recorded (callerRef of the first leg) they are not saved again, but the row still
 * advances.
 */
@Service
@Transactional
class PayslipRecorder(
    private val recurringPayslipRepository: RecurringPayslipRepository,
    private val portfolioRepository: PortfolioRepository,
    private val trnService: TrnService,
    private val trnImportService: TrnImportService
) {
    /** Returns the transactions saved; empty when the message was dropped or already recorded. */
    fun record(payslipTrns: PayslipTrns): Collection<Trn> {
        val payslip = recurringPayslipRepository.findById(payslipTrns.recurringPayslipId).orElse(null)
        val portfolio = payslip?.let { portfolioRepository.findById(it.portfolioId).orElse(null) }
        val dropped =
            when {
                payslip == null || portfolio == null -> {
                    "no longer exists"
                }
                payslipTrns.payDate != payslip.nextPayDate -> {
                    "pay date ${payslipTrns.payDate} is not the next one (${payslip.nextPayDate})"
                }
                payslipTrns.portfolioId != payslip.portfolioId -> {
                    "transactions for portfolio ${payslipTrns.portfolioId}, scheduled for ${payslip.portfolioId}"
                }
                payslipTrns.trns.isEmpty() -> {
                    "no transactions for ${payslipTrns.payDate}"
                }
                else -> {
                    return saveAndAdvance(payslip, portfolio, payslipTrns.trns)
                }
            }
        log.warn("Recurring payslip {}: {}, dropped", payslipTrns.recurringPayslipId, dropped)
        return emptyList()
    }

    private fun saveAndAdvance(
        payslip: RecurringPayslip,
        portfolio: Portfolio,
        trns: List<TrnInput>
    ): Collection<Trn> {
        val saved =
            if (trnImportService.recorded(portfolio, trns.first().callerRef) != null) {
                log.info("Recurring payslip {}: {} already recorded", payslip.id, payslip.nextPayDate)
                emptyList()
            } else {
                trnService.save(portfolio, TrnRequest(portfolio.id, trns))
            }
        recurringPayslipRepository.save(
            payslip.copy(nextPayDate = PayDates.next(payslip.nextPayDate, payslip.dayOfMonth))
        )
        return saved
    }

    companion object {
        private val log = LoggerFactory.getLogger(PayslipRecorder::class.java)
    }
}