package com.beancounter.client

import com.beancounter.client.ingest.Filter
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

internal class TestFilter {
    @Test
    fun is_FilteredAssetsCaseInsensitive() {
        val filter = Filter("Code")
        Assertions.assertThat(filter.hasFilter()).isTrue
        Assertions.assertThat(filter.inFilter(getAsset("Market", "Code")))
            .isTrue
        Assertions.assertThat(filter.inFilter(getAsset("Market", "code")))
            .isTrue
    }

    @Test
    fun is_NotInFilter() {
        val filter = Filter(null)
        Assertions.assertThat(filter.hasFilter()).isFalse
    }
}
