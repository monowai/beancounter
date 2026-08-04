package com.beancounter.common.model

import com.beancounter.common.utils.BcJson.Companion.objectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Verifies JSON (de)serialization behaviour of [Asset.classificationCheckedAt].
 *
 * bc-view's sector-weightings UI relies on this field being present when set, and
 * absent (not `null`) when it has never been checked - it is the only signal available
 * to distinguish "never checked" from "checked, provider had no data" once the
 * exposures list is empty.
 */
class AssetJsonTest {
    private fun asset(classificationCheckedAt: LocalDate?) =
        Asset(
            code = "MSFT",
            market = Market("NASDAQ"),
            classificationCheckedAt = classificationCheckedAt
        )

    @Test
    fun `classificationCheckedAt is serialized when set`() {
        val checkedAt = LocalDate.of(2026, 8, 1)
        val json = objectMapper.writeValueAsString(asset(checkedAt))

        assertThat(json).contains("\"classificationCheckedAt\":\"2026-08-01\"")
    }

    @Test
    fun `classificationCheckedAt is omitted when null`() {
        val json = objectMapper.writeValueAsString(asset(null))

        assertThat(json).doesNotContain("classificationCheckedAt")
    }

    @Test
    fun `classificationCheckedAt round-trips through serialization`() {
        val checkedAt = LocalDate.of(2026, 8, 1)
        val json = objectMapper.writeValueAsString(asset(checkedAt))

        val result = objectMapper.readValue(json, Asset::class.java)

        assertThat(result.classificationCheckedAt).isEqualTo(checkedAt)
    }
}