package com.beancounter.position.composite

import com.beancounter.common.composite.SubAccountBalance
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Position
import org.springframework.stereotype.Component
import java.time.LocalDate
import com.beancounter.common.composite.CompositeValuation as SharedCompositeValuation

/**
 * Composite valuation for Singapore CPF policies. Delegates the sub-account
 * roll-up math to jar-common's [SharedCompositeValuation] so svc-position and
 * svc-retire stay in lock-step. This class adapts svc-position's
 * [PrivateAssetConfigDto] into the shared [SubAccountBalance] shape and
 * packages the result as a [Position] for the `/positions/composite` endpoint.
 *
 * CPF LIFE payout conversion is left to the projection layer in svc-retire;
 * this strategy reports current balance only.
 */
@Component
class CpfValuation(
    private val shared: SharedCompositeValuation
) : CompositeValuation {
    override fun supports(config: PrivateAssetConfigDto): Boolean = config.policyType == POLICY_TYPE

    override fun value(
        asset: Asset,
        config: PrivateAssetConfigDto,
        asAt: LocalDate
    ): Position {
        val balance =
            shared.value(
                policyType = config.policyType,
                subAccounts =
                    config.subAccounts.map {
                        SubAccountBalance(
                            code = it.code,
                            balance = it.balance,
                            liquid = it.liquid
                        )
                    }
            ) ?: error("Composite roll-up unavailable for policy ${config.policyType}")

        val position = Position(asset)
        balance.byCode.forEach { (code, value) -> position.subAccounts[code] = value }
        position.quantityValues.adjustment = balance.total
        return position
    }

    companion object {
        const val POLICY_TYPE: String = "CPF"
    }
}