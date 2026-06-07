package com.beancounter.position.composite

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Position
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Composite valuation for Singapore CPF policies. Sums OA/SA/MA/RA sub-account
 * balances into a single rolled-up [Position]. SG-specific liquidity / CPF
 * LIFE payout conversion is captured via the [SubAccountDto.liquid] flag and
 * deferred to the projection layer; this strategy only reports current balance.
 */
@Component
class CpfValuation : CompositeValuation {
    override fun supports(config: PrivateAssetConfigDto): Boolean = config.policyType == POLICY_TYPE

    override fun value(
        asset: Asset,
        config: PrivateAssetConfigDto,
        asAt: LocalDate
    ): Position {
        val position = Position(asset)
        var total = BigDecimal.ZERO
        for (sub in config.subAccounts) {
            position.subAccounts[sub.code] = sub.balance
            total = total.add(sub.balance)
        }
        position.quantityValues.adjustment = total
        return position
    }

    companion object {
        const val POLICY_TYPE: String = "CPF"
    }
}