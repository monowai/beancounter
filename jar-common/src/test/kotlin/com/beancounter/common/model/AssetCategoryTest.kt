package com.beancounter.common.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AssetCategoryTest {
    @Test
    fun `INDEX constant exposed`() {
        assertThat(AssetCategory.INDEX).isEqualTo("INDEX")
    }

    @Test
    fun `REPORT_INDEX constant exposed`() {
        assertThat(AssetCategory.REPORT_INDEX).isEqualTo("Index")
    }

    @Test
    fun `INDEX category maps to REPORT_INDEX`() {
        assertThat(AssetCategory.toReportCategory(AssetCategory.INDEX))
            .isEqualTo(AssetCategory.REPORT_INDEX)
    }

    @Test
    fun `lowercase index maps to REPORT_INDEX`() {
        assertThat(AssetCategory.toReportCategory("index"))
            .isEqualTo(AssetCategory.REPORT_INDEX)
    }
}