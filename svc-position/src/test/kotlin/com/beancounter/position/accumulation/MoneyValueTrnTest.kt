package com.beancounter.position.accumulation

import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.Constants.Companion.NASDAQ
import com.beancounter.position.Constants.Companion.fourK
import com.beancounter.position.Constants.Companion.hundred
import com.beancounter.position.Constants.Companion.ten
import com.beancounter.position.Constants.Companion.twoK
import com.beancounter.position.utils.CurrencyResolver
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal

/**
 * Tests the lifecycle of a transaction over all supported transaction types and verifies
 * key values in the various currency buckets.
 *
 * Simple FX values make assertions easier to calculate.
 */
@SpringBootTest(classes = [Accumulator::class])
internal class MoneyValueTrnTest {
    private val microsoft = getAsset(NASDAQ, "MSFT")
    private val objectMapper: ObjectMapper = BcJson().objectMapper

    @Autowired
    private lateinit var accumulator: Accumulator

    private val tradePortfolioRate = BigDecimal("1.724")
    private val dateUtils = DateUtils()
    private val buyBehaviour = BuyBehaviour()
    private val dividendBehaviour = DividendBehaviour()
    private val pPurchases = "purchases"
    private val pCostBasis = "costBasis"
    private val pDividends = "dividends"
    private val sellBehaviour = SellBehaviour(CurrencyResolver())
    private val splitBehaviour = SplitBehaviour()

    /**
     * Tests the lifecycle of a transaction over all supported transaction types and verifies
     * key values in the various currency buckets.
     *
     * Simple FX values make assertions easier to calculate.
     */
    @Test
    fun is_ValuedInTrackedCurrencies() {
        val positions = Positions()
        val position = positions[microsoft]
        buyBehaviour.accumulate(
            buyTrn(),
            positions,
        )
        assertThat(position.quantityValues.getTotal())
            .isEqualTo(hundred)
        assertThat(position.getMoneyValues(Position.In.TRADE))
            .hasFieldOrPropertyWithValue(pPurchases, twoK)
            .hasFieldOrPropertyWithValue(pCostBasis, twoK)
        assertThat(position.getMoneyValues(Position.In.BASE))
            .hasFieldOrPropertyWithValue(pPurchases, twoK)
            .hasFieldOrPropertyWithValue(pCostBasis, twoK)

        val amount = BigDecimal("3448.00")
        assertThat(position.getMoneyValues(Position.In.PORTFOLIO))
            .hasFieldOrPropertyWithValue(pPurchases, amount)
            .hasFieldOrPropertyWithValue(pCostBasis, amount)
        dividendBehaviour.accumulate(
            dividendTrn(),
            positions,
        )
        assertThat(position.quantityValues.getTotal())
            .isEqualTo(hundred)
        assertThat(position.getMoneyValues(Position.In.TRADE)).hasFieldOrPropertyWithValue(pDividends, ten)
        assertThat(position.getMoneyValues(Position.In.BASE)).hasFieldOrPropertyWithValue(pDividends, ten)
        assertThat(position.getMoneyValues(Position.In.PORTFOLIO))
            .hasFieldOrPropertyWithValue(pDividends, BigDecimal("17.24"))
        assertThat(position.dateValues.lastDividend).isNotNull
        assertThat(
            dateUtils.isToday(position.dateValues.lastDividend!!.toString()),
        )
        splitBehaviour.accumulate(splitTrn(), positions)

        val deepCopy = objectMapper.readValue(objectMapper.writeValueAsBytes(position), Position::class.java)
        assertThat(position.getMoneyValues(Position.In.TRADE).costBasis)
            .isEqualTo(deepCopy.getMoneyValues(Position.In.TRADE).costBasis)
        assertThat(position.getMoneyValues(Position.In.BASE).costBasis)
            .isEqualTo(deepCopy.getMoneyValues(Position.In.BASE).costBasis)
        assertThat(position.getMoneyValues(Position.In.PORTFOLIO).costBasis)
            .isEqualTo(deepCopy.getMoneyValues(Position.In.PORTFOLIO).costBasis)
        sellBehaviour.accumulate(
            sellTrn(position),
            positions,
        )
        validatePosition(position, amount)
    }

    private fun buyTrn() = Trn(
        trnType = TrnType.BUY,
        asset = microsoft,
        quantity = hundred,
        tradeAmount = twoK,
        tradeCashRate = BigDecimal("0.56"),
        tradePortfolioRate = tradePortfolioRate,
    )

    private fun dividendTrn() = Trn(
        trnType = TrnType.DIVI,
        asset = microsoft,
        quantity = BigDecimal.ZERO,
        tradeAmount = BigDecimal.TEN,
        tradeCashRate = BigDecimal.TEN,
        tradePortfolioRate = tradePortfolioRate,
        cashAmount = BigDecimal.TEN,
    )

    private fun sellTrn(position: Position) = Trn(
        trnType = TrnType.SELL,
        asset = microsoft,
        quantity = position.quantityValues.getTotal(),
        tradeAmount = fourK,
        tradeCashRate = BigDecimal.TEN,
        tradePortfolioRate = tradePortfolioRate,
    )

    private fun splitTrn() = Trn(
        trnType = TrnType.SPLIT,
        asset = microsoft,
        quantity = BigDecimal.TEN,
        tradeCashRate = BigDecimal.TEN,
        tradePortfolioRate = tradePortfolioRate,
        cashAmount = BigDecimal.TEN,
    )

    private fun validatePosition(position: Position, amount: BigDecimal) {
        assertThat(position.getMoneyValues(Position.In.TRADE).sales)
            .isEqualTo(fourK)
        assertThat(position.getMoneyValues(Position.In.TRADE).realisedGain)
            .isEqualTo(twoK)
        assertThat(position.getMoneyValues(Position.In.BASE).sales)
            .isEqualTo(fourK)
        assertThat(position.getMoneyValues(Position.In.BASE).realisedGain)
            .isEqualTo(twoK)
        assertThat(position.getMoneyValues(Position.In.PORTFOLIO).sales)
            .isEqualTo(BigDecimal("6896.00"))
        assertThat(position.getMoneyValues(Position.In.PORTFOLIO).realisedGain)
            .isEqualTo(amount)
    }
}
