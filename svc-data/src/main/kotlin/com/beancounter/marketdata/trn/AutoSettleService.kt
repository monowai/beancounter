package com.beancounter.marketdata.trn

import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.registration.UserPreferencesService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service to auto-settle PROPOSED EVENT transactions when their tradeDate arrives.
 *
 * This service is called by a scheduler to automatically transition dividend and
 * split transactions from PROPOSED to SETTLED status once the pay date is reached.
 * TRADE transactions (BUY, SELL, etc.) are NOT auto-settled.
 *
 * Honours the per-owner `autoSettle` preference: rows whose portfolio owner has
 * disabled auto-settlement are left in PROPOSED so the user can confirm them
 * manually.
 */
@Service
@Transactional
class AutoSettleService(
    private val trnRepository: TrnRepository,
    private val dateUtils: DateUtils,
    private val userPreferencesService: UserPreferencesService,
    private val trnSettlementService: TrnSettlementService
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

        // Cache per-owner preference lookups across the batch so we don't hit
        // the user_preferences table once per transaction.
        val autoSettleByOwner = mutableMapOf<String, Boolean>()
        var settledCount = 0
        for (trn in dueTransactions) {
            val owner = trn.portfolio.owner
            val ownerOptIn =
                autoSettleByOwner.getOrPut(owner.id) {
                    userPreferencesService.getOrCreate(owner).autoSettle
                }
            if (!ownerOptIn) {
                log.debug(
                    "Skipping auto-settle for {} — owner {} has autoSettle disabled",
                    trn.id,
                    owner.id
                )
                continue
            }
            // Shared settle core — FX + status flip + cash emit. Same path as the
            // manual TrnService.settleTransactions, so a scheduled DIVI/SPLIT settle
            // produces its cash leg identically. Null = FX not yet resolvable; leave
            // PROPOSED for the next run.
            trnSettlementService.settle(trn.portfolio, trn) ?: continue
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