package com.beancounter.marketdata.trn

import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.model.Trn
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional

/**
 * Unit tests for [TrnRepository.getOrThrow] extension.
 *
 * Red → green for the new helper; covers the found and not-found paths.
 */
class TrnRepositoryExtensionsTest {
    private val trnRepository: TrnRepository = mock()

    @Test
    fun `getOrThrow returns trn when found`() {
        val trn: Trn = mock()
        whenever(trnRepository.findById("t1")).thenReturn(Optional.of(trn))

        val result = trnRepository.getOrThrow("t1")

        assertThat(result).isSameAs(trn)
    }

    @Test
    fun `getOrThrow throws NotFoundException with id in message when not found`() {
        whenever(trnRepository.findById("missing-id")).thenReturn(Optional.empty())

        assertThatThrownBy { trnRepository.getOrThrow("missing-id") }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessageContaining("missing-id")
    }
}