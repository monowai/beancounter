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
import com.beancounter.marketdata.cash.CASH
import com.beancounter.marketdata.currency.CurrencyService
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Cash calculation services for a pre-populated TrnInput.
 */
@Service
class CashTrnServices(
    private val assetFinder: AssetFinder,
    private val assetService: AssetService,
    val currencyService: CurrencyService
) {
    fun getCashImpact(
        trnInput: TrnInput,
        tradeAmount: BigDecimal = trnInput.tradeAmount
    ): BigDecimal {
        if (TrnType.isCashImpacted(trnInput.trnType)) {
            if (trnInput.cashAmount.compareTo(BigDecimal.ZERO) != 0) {
                return trnInput.cashAmount // Cash amount has been set by the caller
            }
            val rate = trnInput.tradeCashRate
            if (creditsCash.contains(trnInput.trnType)) {
                return MathUtils.divide(
                    tradeAmount.abs(),
                    rate
                )
            } else if (debitsCash.contains(trnInput.trnType)) {
                return MathUtils.divide(
                    BigDecimal.ZERO.minus(tradeAmount.abs()),
                    rate
                )
            }
        }
        return BigDecimal.ZERO
    }

    /**
     * Resolves the cash/settlement asset for a transaction.
     *
     * @param trnType The transaction type
     * @param cashAccountCode Optional code of a specific settlement account (e.g., "SCB-SGD" for a bank account).
     *                        Can be an asset code or UUID for backward compatibility.
     * @param cashCurrency Currency code for generic cash balance fallback (e.g., "SGD")
     * @param ownerId Owner ID for looking up private assets by code
     * @return The resolved Asset, or null if no cash asset is required
     */
    fun getCashAsset(
        trnType: TrnType,
        cashAccountCode: String?,
        cashCurrency: String?,
        ownerId: String? = null
    ): Asset? {
        if (!TrnType.isCashImpacted(trnType)) {
            return null // No cash asset required
        }

        // If a specific cash account code is provided, try to resolve it
        if (!cashAccountCode.isNullOrEmpty()) {
            // First, try to find by UUID (backward compatibility)
            try {
                val foundById = assetFinder.find(cashAccountCode)
                // If found by UUID, return it
                return foundById
            } catch (_: Exception) {
                // Not a UUID or not found, continue to try as asset code
            }

            // Try to find as a private asset by code
            if (!ownerId.isNullOrEmpty()) {
                val privateAsset =
                    assetFinder.findLocally(
                        AssetInput(
                            market = "PRIVATE",
                            code = cashAccountCode,
                            owner = ownerId
                        )
                    )
                if (privateAsset != null) {
                    return privateAsset
                }
            }
        }

        // Fall back to generic cash balance
        if (cashCurrency.isNullOrEmpty()) {
            return null // no cash to look up
        }
        return assetService.findOrCreate(
            AssetInput(
                CASH,
                cashCurrency
            )
        )
    }
}