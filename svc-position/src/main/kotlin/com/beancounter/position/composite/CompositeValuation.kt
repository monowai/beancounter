package com.beancounter.position.composite

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Position
import java.time.LocalDate

/**
 * Strategy for valuing composite assets (CPF, ILP, generic pensions) whose
 * balance lives in sub-accounts rather than transaction history. Each impl
 * decides whether it supports a given asset's config and, if so, synthesises
 * a [Position] with [Position.subAccounts] populated and the rolled-up total
 * exposed via [com.beancounter.common.model.QuantityValues.getTotal].
 */
interface CompositeValuation {
    fun supports(config: PrivateAssetConfigDto): Boolean

    fun value(
        asset: Asset,
        config: PrivateAssetConfigDto,
        asAt: LocalDate
    ): Position
}