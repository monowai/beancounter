package com.beancounter.common.contracts

import com.beancounter.common.model.Market
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * svc-position reads transactions from svc-data as a [TrnResponse]: the cost date has to
 * survive that trip, or performance falls back to the trade date.
 */
class TrnResponseCostDateJsonTest {
    private fun add(costDate: LocalDate?) =
        Trn(
            trnType = TrnType.ADD,
            asset = AssetUtils.getTestAsset(Market("US"), "BRK.B"),
            quantity = BigDecimal.TEN,
            tradeDate = LocalDate.of(2026, 11, 18),
            tradeAmount = BigDecimal("1157.10"),
            costDate = costDate
        )

    private fun roundTrip(trn: Trn): Trn {
        val json = objectMapper.writeValueAsString(TrnResponse(listOf(trn)))
        return objectMapper
            .readValue(json, TrnResponse::class.java)
            .data
            .toTrns()
            .single()
    }

    @Test
    fun `should carry the cost date through a transaction response`() {
        val costDate = LocalDate.of(2016, 11, 18)

        assertThat(roundTrip(add(costDate)).costDate).isEqualTo(costDate)
    }

    @Test
    fun `should carry no cost date when the transaction has none`() {
        assertThat(roundTrip(add(null)).costDate).isNull()
    }
}