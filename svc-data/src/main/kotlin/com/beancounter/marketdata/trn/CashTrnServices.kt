package com.beancounter.marketdata.trn

import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.TrnType
import com.beancounter.common.model.TrnType.Companion.creditsCash
import com.beancounter.common.model.TrnType.Companion.debitsCash
import com.beancounter.common.utils.MathUtils
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.broker.BrokerSettlementAccountRepository
import com.beancounter.marketdata.cash.CASH
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.providers.custom.PrivateMarketDataProvider
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Cash calculation services for a pre-populated TrnInput.
 */
@Service
class CashTrnServices(
    private val assetFinder: AssetFinder,
    private val assetService: AssetService,
    val currencyService: CurrencyService,
    private val brokerSettlementAccountRepository: BrokerSettlementAccountRepository
) {
    fun getCashImpact(
        trnInput: TrnInput,
        tradeAmount: BigDecimal = trnInput.tradeAmount
    ): BigDecimal =
        when {
            !TrnType.isCashImpacted(trnInput.trnType) -> BigDecimal.ZERO
            // Cash amount has been set by the caller
            trnInput.cashAmount.compareTo(BigDecimal.ZERO) != 0 -> trnInput.cashAmount
            else -> cashImpact(trnInput.trnType, tradeAmount, trnInput.tradeCashRate)
        }

    companion object {
        /**
         * Cash effect of a trade amount at the given trade-to-cash rate: positive for
         * types that credit cash, negative for types that debit it, zero otherwise
         * (including an unset rate).
         */
        fun cashImpact(
            trnType: TrnType,
            tradeAmount: BigDecimal,
            tradeCashRate: BigDecimal
        ): BigDecimal =
            when {
                trnType in creditsCash -> MathUtils.divide(tradeAmount.abs(), tradeCashRate)
                trnType in debitsCash -> MathUtils.divide(tradeAmount.abs().negate(), tradeCashRate)
                else -> BigDecimal.ZERO
            }
    }

    /**
     * Resolves the cash/settlement asset for a transaction.
     *
     * @param trnType The transaction type
     * @param cashAccountCode Optional code of a specific settlement account (e.g., "SCB-SGD" for a bank account).
     *                        Can be an asset code or UUID for backward compatibility.
     * @param cashCurrency Currency code for generic cash balance fallback (e.g., "SGD")
     * @param ownerId Owner ID for looking up private assets by code
     * @param brokerId Optional broker whose per-currency settlement account is used
     *                 when no explicit cash account is supplied
     * @return The resolved Asset, or null if no cash asset is required
     */
    fun getCashAsset(
        trnType: TrnType,
        cashAccountCode: String?,
        cashCurrency: String?,
        ownerId: String? = null,
        brokerId: String? = null
    ): Asset? {
        if (!TrnType.isCashImpacted(trnType)) {
            return null // No cash asset required
        }
        // A specific cash account, then the broker's designated account for this
        // currency, then the generic cash balance.
        return findCashAccount(cashAccountCode, ownerId)
            ?: findBrokerSettlementAccount(brokerId, cashCurrency)
            ?: findGenericCash(cashCurrency)
    }

    private fun findCashAccount(
        cashAccountCode: String?,
        ownerId: String?
    ): Asset? {
        if (cashAccountCode.isNullOrEmpty()) {
            return null
        }
        // First, try to find by UUID (backward compatibility). findOrNull, not
        // find: a miss here is expected (bc-view sends the settlement account's
        // code, not its id) and AssetFinder is @Transactional, so letting
        // NotFoundException escape it would mark this participating transaction
        // rollback-only. Catching it does not undo that — TrnService.saveWithResult
        // then died at commit with UnexpectedRollbackException (a 500 on a trn
        // that had otherwise saved cleanly).
        return assetFinder.findOrNull(cashAccountCode) ?: findPrivateCashAccount(cashAccountCode, ownerId)
    }

    // Try to find as a private asset by code
    private fun findPrivateCashAccount(
        cashAccountCode: String,
        ownerId: String?
    ): Asset? =
        if (ownerId.isNullOrEmpty()) {
            null
        } else {
            assetFinder.findLocally(
                AssetInput(
                    market = PrivateMarketDataProvider.ID,
                    code = cashAccountCode,
                    owner = ownerId
                )
            )
        }

    private fun findBrokerSettlementAccount(
        brokerId: String?,
        cashCurrency: String?
    ): Asset? =
        if (brokerId.isNullOrEmpty() || cashCurrency.isNullOrEmpty()) {
            null
        } else {
            brokerSettlementAccountRepository
                .findByBrokerIdAndCurrencyCode(
                    brokerId,
                    cashCurrency
                )?.account
        }

    private fun findGenericCash(cashCurrency: String?): Asset? =
        if (cashCurrency.isNullOrEmpty()) {
            null // no cash to look up
        } else {
            assetService.findOrCreate(
                AssetInput(
                    CASH,
                    cashCurrency
                )
            )
        }
}