package com.beancounter.agent.tools

import com.beancounter.agent.client.PositionClient
import com.beancounter.agent.client.TrnClient
import com.beancounter.common.model.Position
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.temporal.ChronoUnit

/**
 * One asset in one portfolio — open or already sold — with the user's own
 * trade prices, so "how was my exit?" or "how did my entry work out?" can be
 * answered against the current close.
 *
 * Every price is per unit in the asset's trade currency, the same currency as
 * the current close, so the comparison never mixes currencies. Quantities and
 * amounts are never returned, matching [ResponseScrubber].
 */
@Service
class HoldingTools(
    private val positionClient: PositionClient,
    private val trnClient: TrnClient,
    private val dateUtils: DateUtils = DateUtils()
) {
    private companion object {
        const val RATIO_SCALE = 6

        // Mirrors svc-position's `beancounter.irr` default: below this many days held it
        // reports simple ROI, at or above it annualised XIRR.
        const val MIN_DAYS_FOR_IRR = 365L
        val TRADE_TYPES = setOf(TrnType.BUY, TrnType.SELL)
    }

    @Tool(description = HOLDING_DESC)
    fun getHolding(
        @ToolParam(description = "Portfolio code as the user types it, e.g. 'DBS'") portfolioCode: String,
        @ToolParam(description = "Asset code (ticker), e.g. 'RING'") assetCode: String,
        @ToolParam(
            description = "Market code, e.g. 'US' or 'LSE'. Only needed when the ticker is listed on more than one.",
            required = false
        ) market: String? = null
    ): ScrubbedHolding {
        val positions = positionClient.getPositionsByCode(portfolioCode, DateUtils.TODAY, includeValues = true).data
        val candidates =
            positions.positions.values.filter {
                it.asset.code.equals(assetCode, ignoreCase = true) &&
                    (
                        market.isNullOrBlank() ||
                            it.asset.market.code
                                .equals(market, ignoreCase = true)
                    )
            }
        val code = assetCode.uppercase()
        return when (candidates.size) {
            0 -> {
                ScrubbedHolding(portfolioCode, code, status = HoldingStatus.NOT_HELD)
            }
            1 -> {
                describe(portfolioCode, positions.portfolio.id, candidates.single())
            }
            else -> {
                ScrubbedHolding(
                    portfolioCode,
                    code,
                    status = HoldingStatus.AMBIGUOUS,
                    markets = candidates.map { it.asset.market.code }
                )
            }
        }
    }

    private fun describe(
        portfolioCode: String,
        portfolioId: String,
        position: Position
    ): ScrubbedHolding {
        val asset = position.asset
        val trade = position.moneyValues[Position.In.TRADE]
        val close = trade?.priceData?.close?.takeIf { it.signum() > 0 }
        val trades =
            trnClient
                .getTrades(portfolioId, asset.id)
                .data.trns
                .filter { it.trnType in TRADE_TYPES && it.price != null }
                .sortedBy { it.tradeDate }
        val lastSell = trades.lastOrNull { it.trnType == TrnType.SELL }
        val closed = position.quantityValues.getTotal().signum() == 0
        val opened = position.dateValues.opened
        val heldUntil = if (closed) position.dateValues.last ?: dateUtils.date else dateUtils.date
        return ScrubbedHolding(
            portfolioCode = portfolioCode,
            assetCode = asset.code,
            assetName = asset.name,
            market = asset.market.code,
            currency = trade?.currency?.code,
            status = if (closed) HoldingStatus.CLOSED else HoldingStatus.OPEN,
            opened = opened?.toString(),
            lastTrade = position.dateValues.last?.toString(),
            returnRatio = trade?.irr?.toDouble(),
            returnBasis =
                opened?.let {
                    if (ChronoUnit.DAYS.between(it, heldUntil) <
                        MIN_DAYS_FOR_IRR
                    ) {
                        ReturnBasis.SIMPLE
                    } else {
                        ReturnBasis.ANNUALISED
                    }
                },
            currentPrice = close?.toDouble(),
            currentPriceDate = close?.let { trade.priceData.priceDate.toString() },
            lastSellDate = lastSell?.tradeDate?.toString(),
            lastSellPrice = lastSell?.price?.toDouble(),
            changeSinceLastSell =
                lastSell?.price?.let { sold ->
                    close?.subtract(sold)?.divide(sold, RATIO_SCALE, RoundingMode.HALF_UP)?.toDouble()
                },
            trades = trades.map { HoldingTrade(it.tradeDate.toString(), it.trnType.name, it.price!!.toDouble()) }
        )
    }
}

enum class HoldingStatus { OPEN, CLOSED, NOT_HELD, AMBIGUOUS }

/** How [ScrubbedHolding.returnRatio] was computed by svc-position. */
enum class ReturnBasis {
    /** Held under a year: simple return since opened — never describe it as per annum. */
    SIMPLE,

    /** Held a year or more: XIRR, per annum. */
    ANNUALISED
}

/** A buy or sell at a per-unit price in the holding's trade currency. */
data class HoldingTrade(
    val date: String,
    val type: String,
    val price: Double
)

data class ScrubbedHolding(
    val portfolioCode: String,
    val assetCode: String,
    val status: HoldingStatus,
    val assetName: String? = null,
    val market: String? = null,
    /** Trade currency: [currentPrice] and every trade price are in it. */
    val currency: String? = null,
    val opened: String? = null,
    val lastTrade: String? = null,
    /** Return over the life of the holding, as a ratio; see [returnBasis]. */
    val returnRatio: Double? = null,
    val returnBasis: ReturnBasis? = null,
    val currentPrice: Double? = null,
    val currentPriceDate: String? = null,
    val lastSellDate: String? = null,
    val lastSellPrice: Double? = null,
    /** (currentPrice − lastSellPrice) / lastSellPrice; positive means the price rose after the sale. */
    val changeSinceLastSell: Double? = null,
    val trades: List<HoldingTrade> = emptyList(),
    /** Set when [status] is AMBIGUOUS: the markets the ticker is held on. */
    val markets: List<String> = emptyList()
)

const val HOLDING_DESC =
    "One asset in one named portfolio, including a holding the user has since SOLD (closed). " +
        "Use it whenever the user asks about their own past or present holding of a ticker — " +
        "entry or exit timing, 'how was my exit', 'did I sell too early', 'what did I pay'. " +
        "Resolve a portfolio the user names (e.g. 'my DBS portfolio') with listPortfolios first. " +
        "Returns status OPEN / CLOSED / NOT_HELD / AMBIGUOUS (then retry with one of `markets`), " +
        "the user's buy and sell prices per unit, lastSellPrice and changeSinceLastSell " +
        "(current close vs the last sale, as a ratio: 0.10 = the price is 10% above where they sold), " +
        "and returnRatio over the life of the holding — per annum only when returnBasis is ANNUALISED; " +
        "SIMPLE means a plain return since opened (held under a year), never say 'p.a.' for it. " +
        "All prices are per unit in `currency`. " +
        "No quantities or amounts are available — answer in prices and percentages."