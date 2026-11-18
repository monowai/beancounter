package com.beancounter.common.model

import com.beancounter.common.utils.AssetUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * A holding added with the date its cost was paid opens on that date.
 */
class PositionsCostDateTest {
    private val asset = AssetUtils.getTestAsset(Market("US"), "BRK.B")
    private val recordDate = LocalDate.of(2026, 11, 18)
    private val heldSince = LocalDate.of(2016, 11, 18)

    private fun trn(
        trnType: TrnType,
        costDate: LocalDate?
    ) = Trn(
        trnType = trnType,
        asset = asset,
        quantity = BigDecimal.TEN,
        tradeDate = recordDate,
        tradeAmount = BigDecimal("1157.10"),
        costDate = costDate
    )

    @Test
    fun `should open an add with a cost date on the cost date`() {
        val position = Positions().getOrCreate(trn(TrnType.ADD, heldSince))

        assertThat(position.dateValues.opened).isEqualTo(heldSince)
    }

    @Test
    fun `should open an add without a cost date on the trade date`() {
        val position = Positions().getOrCreate(trn(TrnType.ADD, null))

        assertThat(position.dateValues.opened).isEqualTo(recordDate)
    }

    @Test
    fun `should open a buy on the trade date, whatever cost date it carries`() {
        val position = Positions().getOrCreate(trn(TrnType.BUY, heldSince))

        assertThat(position.dateValues.opened).isEqualTo(recordDate)
    }
}