package com.beancounter.position.accumulation

import com.beancounter.common.model.Market
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.Constants.Companion.AUD
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal

@SpringBootTest(classes = [Accumulator::class])
internal class DividendTest {
    @Autowired
    private lateinit var accumulator: Accumulator

    @Test
    fun is_CashDividendAccumulated() {
        val asx =
            Market(
                "ASX",
                AUD.code
            )
        val asset =
            getTestAsset(
                asx,
                "MO"
            )
        val trn =
            Trn(
                trnType = TrnType.DIVI,
                asset = asset
            )
        trn.tradeCashRate = BigDecimal("0.8988")
        trn.tradeAmount = BigDecimal("12.99")
        val position =
            accumulator.accumulate(
                trn,
                Positions()
            )
        assertThat(
            position.getMoneyValues(
                Position.In.TRADE,
                asset.market.currency
            )
        ).hasFieldOrPropertyWithValue(
            "dividends",
            trn.tradeAmount
        )
    }

    @Test
    fun is_LastDividendDateSetOnDividendAccumulation() {
        val asx = Market("ASX", AUD.code)
        val asset = getTestAsset(asx, "MO")
        val dateUtils = DateUtils()
        val dividendDate = dateUtils.getFormattedDate("2023-06-15")

        val trn =
            Trn(
                trnType = TrnType.DIVI,
                asset = asset,
                tradeDate = dividendDate
            )
        trn.tradeAmount = BigDecimal("12.99")

        val position = accumulator.accumulate(trn, Positions())

        assertThat(position.dateValues.lastDividend).isEqualTo(dividendDate)
    }
}