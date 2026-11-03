package com.beancounter.marketdata.classification

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Unit tests for [ClassificationRefreshController]: how a refresh outcome is reported.
 */
class ClassificationRefreshControllerTest {
    private val classificationRefreshService = mock<ClassificationRefreshService>()
    private val controller = ClassificationRefreshController(classificationRefreshService)

    @Test
    fun `refreshAsset reports both refreshed and result for a rate-limited outcome`() {
        whenever(classificationRefreshService.refreshAsset("asset-1")).thenReturn(EnrichmentResult.RATE_LIMITED)

        val response = controller.refreshAsset("asset-1")

        assertThat(response["refreshed"]).isEqualTo(false)
        assertThat(response["result"]).isEqualTo("RATE_LIMITED")
    }

    @Test
    fun `refreshAssetByCode reports refreshed true and result ENRICHED on success`() {
        whenever(classificationRefreshService.refreshAssetByCode("NASDAQ", "SCHG"))
            .thenReturn(EnrichmentResult.ENRICHED)

        val response = controller.refreshAssetByCode("NASDAQ", "SCHG")

        assertThat(response["refreshed"]).isEqualTo(true)
        assertThat(response["result"]).isEqualTo("ENRICHED")
    }
}