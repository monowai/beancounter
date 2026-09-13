package com.beancounter.marketdata.news

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

/**
 * [VectorMath] underpins every similarity decision in the news pipeline — dedup clustering and
 * derived-topic tagging both reduce cosine similarity to a dot product over unit vectors.
 */
internal class VectorMathTest {
    @Test
    fun `normalize returns a unit-length vector`() {
        val normalized = VectorMath.normalize(floatArrayOf(3f, 4f))

        assertThat(normalized.toList()).containsExactly(0.6f, 0.8f)
        assertThat(sqrt(normalized.sumOf { it.toDouble() * it.toDouble() })).isCloseTo(1.0, within())
    }

    @Test
    fun `normalize leaves a zero vector alone rather than dividing by zero`() {
        assertThat(VectorMath.normalize(floatArrayOf(0f, 0f)).toList()).containsExactly(0f, 0f)
    }

    @Test
    fun `dot of orthogonal unit vectors is zero and of identical ones is one`() {
        assertThat(VectorMath.dot(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f))).isCloseTo(0.0, within())
        assertThat(VectorMath.dot(floatArrayOf(1f, 0f), floatArrayOf(1f, 0f))).isCloseTo(1.0, within())
    }

    /**
     * Truncating to the shorter vector produced a number that looked like a cosine similarity but
     * wasn't — a 384-dim vector compared against the first 384 dimensions of a 768-dim one reads as
     * a near-duplicate. Callers must decide what a mismatch means; silently answering is not an
     * option here.
     */
    @Test
    fun `dot rejects mismatched dimensions instead of truncating`() {
        assertThatIllegalArgumentException()
            .isThrownBy { VectorMath.dot(floatArrayOf(1f, 0f), floatArrayOf(1f, 0f, 0f)) }
            .withMessageContaining("2")
            .withMessageContaining("3")
    }

    private fun within() =
        org.assertj.core.data.Offset
            .offset(1e-6)
}