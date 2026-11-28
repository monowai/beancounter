package com.beancounter.common.model

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * The rules for how a position's quantity may change.
 */
class QuantityValuesTest {
    private fun held(quantity: String) = QuantityValues().apply { buy(BigDecimal(quantity)) }

    @Test
    fun `should add what is bought to what is held`() {
        val quantity = held("100")
        quantity.buy(BigDecimal("50"))

        assertThat(quantity.purchased).isEqualByComparingTo("150")
        assertThat(quantity.getTotal()).isEqualByComparingTo("150")
    }

    @Test
    fun `should take a disposal out whichever way it is signed`() {
        val quantity = held("100")
        quantity.dispose(BigDecimal("30"))
        quantity.dispose(BigDecimal("-20"))

        assertThat(quantity.sold).isEqualByComparingTo("-50")
        assertThat(quantity.getTotal()).isEqualByComparingTo("50")
    }

    @Test
    fun `should reject a disposal of more than is held and change nothing`() {
        val quantity = held("50")

        assertThatThrownBy { quantity.dispose(BigDecimal("50.001")) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("50.001")
            .hasMessageContaining("50 held")

        assertThat(quantity.getTotal()).isEqualByComparingTo("50")
    }

    @Test
    fun `should let a debit take cash below zero`() {
        val cash = held("100")
        cash.debit(BigDecimal("150"))
        cash.debit(BigDecimal("-25"))

        assertThat(cash.sold).isEqualByComparingTo("-175")
        assertThat(cash.getTotal()).isEqualByComparingTo("-75")
    }

    @Test
    fun `should compound a split over everything held`() {
        val quantity = held("100")
        quantity.applySplit(BigDecimal("2"))
        quantity.buy(BigDecimal("100"))
        quantity.dispose(BigDecimal("50"))
        quantity.applySplit(BigDecimal("2"))

        assertThat(quantity.getTotal()).isEqualByComparingTo("500")
        assertThat(quantity.purchased).isEqualByComparingTo("200")
    }

    @Test
    fun `should replace everything held with an absolute quantity`() {
        val quantity = held("100")
        quantity.dispose(BigDecimal("30"))
        quantity.applySplit(BigDecimal("3"))
        quantity.resetTo(BigDecimal("5000"))

        assertThat(quantity.getTotal()).isEqualByComparingTo("5000")
        assertThat(quantity.sold).isEqualByComparingTo("0")
        assertThat(quantity.adjustment).isEqualByComparingTo("0")
    }

    @Test
    fun `should hold exactly zero in every bucket once reset to nothing`() {
        val quantity = held("100.50")
        quantity.applySplit(BigDecimal("2"))
        quantity.resetTo(BigDecimal.ZERO)

        assertThat(quantity.purchased).isEqualTo(BigDecimal.ZERO)
        assertThat(quantity.sold).isEqualTo(BigDecimal.ZERO)
        assertThat(quantity.adjustment).isEqualTo(BigDecimal.ZERO)
        assertThat(quantity.hasPosition()).isFalse()
    }

    @Test
    fun `should sum another position's quantity bucket by bucket`() {
        val quantity = held("100")
        quantity.dispose(BigDecimal("10"))
        val other = held("40")
        other.applySplit(BigDecimal("2"))
        other.dispose(BigDecimal("5"))

        quantity.add(other)

        assertThat(quantity.purchased).isEqualByComparingTo("140")
        assertThat(quantity.sold).isEqualByComparingTo("-15")
        assertThat(quantity.adjustment).isEqualByComparingTo("40")
        assertThat(quantity.getTotal()).isEqualByComparingTo("165")
    }

    @Test
    fun `should keep every bucket through JSON`() {
        val quantity = held("100")
        quantity.dispose(BigDecimal("10"))
        quantity.applySplit(BigDecimal("2"))

        val json = objectMapper.writeValueAsString(quantity)
        val read = objectMapper.readValue(json, QuantityValues::class.java)

        assertThat(read.purchased).isEqualByComparingTo("100")
        assertThat(read.sold).isEqualByComparingTo("-10")
        assertThat(read.adjustment).isEqualByComparingTo("90")
        assertThat(read.getTotal()).isEqualByComparingTo("180")
        assertThat(objectMapper.readValue(json, Map::class.java).keys)
            .containsExactlyInAnyOrder("sold", "purchased", "adjustment", "total", "precision")
    }
}