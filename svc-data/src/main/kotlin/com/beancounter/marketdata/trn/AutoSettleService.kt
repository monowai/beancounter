package com.beancounter.marketdata.trn

import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service to auto-settle PROPOSED EVENT transactions when their tradeDate arrives.
 *
 * This service is called by a scheduler to automatically transition dividend and
 * split transactions from PROPOSED to SETTLED status once the pay date is reached.
 * TRADE transactions (BUY, SELL, etc.) are NOT auto-settled.
 */
@Service
@Transactional
class AutoSettleService(
    private val trnRepository: TrnRepository,
    private val dateUtils: DateUtils
) {
    private val log = LoggerFactory.getLogger(AutoSettleService::class.java)

    companion object {
        /**
         * Transaction types that are considered events and can be auto-settled.
         * TRADE transactions (BUY, SELL, etc.) are excluded.
         */
        val EVENT_TYPES = listOf(TrnType.DIVI, TrnType.SPLIT)
    }

    /**
     * Find all PROPOSED EVENT transactions where tradeDate <= today and settle them.
     * Only DIVI and SPLIT transactions are auto-settled; TRADE transactions are not.
     *
     * @return Number of transactions that were settled
     */
    fun autoSettleDueTransactions(): Int {
        val today = dateUtils.date
        val dueTransactions =
            trnRepository.findDueEventTransactions(
                TrnStatus.PROPOSED,
                EVENT_TYPES,
                today
            )

        if (dueTransactions.isEmpty()) {
            log.debug("No PROPOSED event transactions due for auto-settlement")
            return 0
        }

        var settledCount = 0
        for (trn in dueTransactions) {
            trn.status = TrnStatus.SETTLED
            trnRepository.save(trn)
            settledCount++
            log.info(
                "Auto-settled transaction: {}, asset: {}, portfolio: {}, tradeDate: {}",
                trn.id,
                trn.asset.code,
                trn.portfolio.code,
                trn.tradeDate
            )
        }

        log.info("Auto-settled {} transactions", settledCount)
        return settledCount
    }
}