package com.beancounter.client

import com.beancounter.client.Constants.Companion.NYSE
import com.beancounter.client.ingest.Filter
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

internal class TestFilter {
    @Test
    fun is_FilteredAssetsCaseInsensitive() {
        val filter = Filter("Code")
        Assertions.assertThat(filter.hasFilter()).isTrue
        Assertions.assertThat(filter.inFilter(getTestAsset(NYSE, "Code")))
            .isTrue
        Assertions.assertThat(filter.inFilter(getTestAsset(NYSE, "code")))
            .isTrue
    }

    @Test
    fun is_NotInFilter() {
        val filter = Filter(null)
        Assertions.assertThat(filter.hasFilter()).isFalse
    }
}
