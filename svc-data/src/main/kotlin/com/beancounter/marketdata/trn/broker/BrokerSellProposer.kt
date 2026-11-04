package com.beancounter.marketdata.trn.broker

import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.registration.SystemUserService
import com.beancounter.marketdata.trn.BrokerPortfolioGroup
import com.beancounter.marketdata.trn.BrokerProposalRequest
import com.beancounter.marketdata.trn.TrnRepository
import com.beancounter.marketdata.trn.TrnService
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Turns a weighted sell request from the broker reconciliation view into PROPOSED SELL trns,
 * sized from the split-adjusted holdings [TrnBrokerService.getBrokerHoldings] reports.
 */
@Service
@Transactional
class BrokerSellProposer(
    private val trnRepository: TrnRepository,
    private val systemUserService: SystemUserService,
    private val trnBrokerService: TrnBrokerService,
    private val assetFinder: AssetFinder,
    private val trnService: TrnService,
    private val portfolioService: PortfolioService
) {
    /**
     * Create weighted PROPOSED SELL transactions from the broker reconciliation
     * view — one per portfolio holding [BrokerProposalRequest.assetId] at the
     * broker, sized as `weight` * that portfolio's split-adjusted broker holding,
     * rounded to the asset's board lot (see [roundToLot]).
     * No cash auto-settle fires: proposals are PROPOSED, not SETTLED.
     */
    fun proposeWeighted(
        brokerId: String,
        request: BrokerProposalRequest
    ): TrnResponse {
        rejection(brokerId, request)?.let { throw BusinessException(it) }

        val position =
            trnBrokerService
                .getBrokerHoldings(brokerId)
                .holdings
                .firstOrNull { it.assetId == request.assetId }
                ?: throw NotFoundException("Asset not held at broker: ${request.assetId}")

        // Resolve trade currency up front (same fallback CSV import uses via
        // BcRowAdapter.getTradeCurrency) so FxTransactions.setRates compares
        // like-for-like currencies instead of tripping over the TrnInput
        // default empty tradeCurrency, which reads as an unknown "" currency.
        val asset = assetFinder.find(request.assetId)
        val proposal =
            Proposal(
                brokerId = brokerId,
                request = request,
                tradeCurrency = asset.accountingType?.currency?.code ?: asset.market.currency.code,
                tradeDate = request.tradeDate ?: DateUtils().date
            )

        val created = mutableListOf<Trn>()
        for (group in position.portfolioGroups.filter { it.quantity.signum() > 0 }) {
            val quantity = roundToLot(group.quantity.multiply(request.weight), position.boardLot, group.quantity)
            if (quantity.signum() != 0) {
                created.addAll(save(group, proposal.sellInput(quantity)))
            }
        }
        return TrnResponse(created)
    }

    /**
     * Why [request] cannot be proposed at [brokerId], or null when it can. Request checks run
     * before the duplicate check, which needs the caller.
     */
    private fun rejection(
        brokerId: String,
        request: BrokerProposalRequest
    ): String? =
        when {
            request.trnType != TrnType.SELL -> {
                "Only SELL proposals are supported"
            }
            request.weight <= BigDecimal.ZERO || request.weight > BigDecimal.ONE -> {
                "Weight must be greater than 0 and less than or equal to 1"
            }
            request.price <= BigDecimal.ZERO -> {
                "Price must be greater than 0"
            }
            brokerId == TrnBrokerService.NO_BROKER -> {
                "A broker is required to propose transactions"
            }
            trnRepository.existsByBrokerAndAssetAndTypeAndStatus(
                brokerId,
                request.assetId,
                TrnType.SELL,
                TrnStatus.PROPOSED,
                systemUserService.getOrThrow()
            ) -> {
                "A proposed sell already exists for this asset at this broker"
            }
            else -> {
                null
            }
        }

    private fun save(
        group: BrokerPortfolioGroup,
        trnInput: TrnInput
    ): List<Trn> {
        val portfolio = portfolioService.find(group.portfolioId)
        return trnService.saveWithResult(portfolio, TrnRequest(portfolio.id, listOf(trnInput))).trns
    }

    /**
     * Round [raw] to the asset's board lot. Lot 0 = fractional allowed (6dp,
     * HALF_UP). Otherwise the nearest whole lot multiple, capped at [held]
     * rounded DOWN to a full lot so a proposal never oversells — a fractional
     * holding (e.g. DRIP residue) therefore floors to fewer units than
     * weight * held, and a holding below one lot yields zero (skipped).
     */
    private fun roundToLot(
        raw: BigDecimal,
        boardLot: Int,
        held: BigDecimal
    ): BigDecimal {
        if (boardLot < 1) return raw.setScale(UNLOTTED_QUANTITY_SCALE, RoundingMode.HALF_UP)
        val lot = boardLot.toBigDecimal()
        val rounded = raw.divide(lot, 0, RoundingMode.HALF_UP).multiply(lot)
        if (rounded > held) {
            return held.divide(lot, 0, RoundingMode.DOWN).multiply(lot)
        }
        return rounded
    }

    /** What every leg of one weighted sell shares: broker, price, trade currency and date. */
    private data class Proposal(
        val brokerId: String,
        val request: BrokerProposalRequest,
        val tradeCurrency: String,
        val tradeDate: LocalDate
    ) {
        fun sellInput(quantity: BigDecimal): TrnInput {
            val tradeAmount = quantity.multiply(request.price).setScale(2, RoundingMode.HALF_UP)
            return TrnInput(
                assetId = request.assetId,
                trnType = TrnType.SELL,
                quantity = quantity,
                tradeCurrency = tradeCurrency,
                price = request.price,
                tradeAmount = tradeAmount,
                cashAmount = tradeAmount,
                status = TrnStatus.PROPOSED,
                brokerId = brokerId,
                tradeDate = tradeDate
            )
        }
    }

    private companion object {
        const val UNLOTTED_QUANTITY_SCALE = 6
    }
}