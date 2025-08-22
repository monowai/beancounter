package com.beancounter.client

import com.beancounter.client.Constants.Companion.NYSE
import com.beancounter.client.ingest.Filter
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class TestFilter {
    @Test
    fun `should filter assets case insensitive`() {
        val filter = Filter("Code")
        assertThat(filter.hasFilter()).isTrue
        assertThat(
            filter.inFilter(
                getTestAsset(
                    NYSE,
                    "Code"
                )
            )
        ).isTrue
        assertThat(
            filter.inFilter(
                getTestAsset(
                    NYSE,
                    "code"
                )
            )
        ).isTrue
    }

    @Test
    fun `should not be in filter when filter is null`() {
        assertThat(Filter(null).hasFilter()).isFalse
    }
}