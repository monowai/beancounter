package com.beancounter.marketdata.broker.reconcile

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.TrnType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

/** A back-dated row put into the share basis of its own date. */
class ConfirmedAssetsTest {
    private val importSplits = mock<ImportSplits>()
    private val confirmedAssets = ConfirmedAssets(mock(), mock(), mock(), importSplits)
    private val held = LocalDate.of(2021, 1, 4)
    private val recordDate = LocalDate.of(2026, 11, 18)
    private val row =
        ConfirmedRow(
            assetId = "splt",
            trnType = TrnType.ADD,
            quantity = BigDecimal("40"),
            cost = BigDecimal("1000"),
            tradeDate = held
        )

    @Test
    fun `should divide the quantity by the splits since its date`() {
        whenever(importSplits.factor("splt", held, recordDate)).thenReturn(BigDecimal("20"))

        val (based, note) = confirmedAssets.onItsDate(row, recordDate)

        assertThat(based.quantity).isEqualByComparingTo("2")
        assertThat(note).contains("40", "2", "20")
    }

    @Test
    fun `should keep the quantity shown and say so when the splits cannot be known`() {
        whenever(importSplits.factor("splt", held, recordDate)).thenReturn(null)

        val (based, note) = confirmedAssets.onItsDate(row, recordDate)

        assertThat(based).isEqualTo(row)
        assertThat(note).contains("unavailable", "not applied")
    }

    @Test
    fun `should refuse a back-dated row whose quantity before the splits rounds to nothing`() {
        whenever(importSplits.factor("splt", held, recordDate)).thenReturn(BigDecimal("20"))

        assertThatThrownBy { confirmedAssets.onItsDate(row.copy(quantity = BigDecimal("0.000001")), recordDate) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContainingAll("splt", "0.000001", "20")
    }
}