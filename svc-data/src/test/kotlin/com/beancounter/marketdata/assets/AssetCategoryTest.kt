package com.beancounter.marketdata.assets

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(classes = [AssetCategoryConfig::class])
internal class AssetCategoryTest {
    @Autowired
    lateinit var assetCategoryConfig: AssetCategoryConfig

    @Test
    fun `should set values from config`() {
        assertThat(assetCategoryConfig)
            .hasFieldOrProperty("default")
            .hasFieldOrProperty("values")
    }

    @Test
    fun `should find default config`() {
        assertThat(assetCategoryConfig.get()).isNotNull
    }

    @Test
    fun `should find equity config`() {
        assertThat(assetCategoryConfig.get("equity")).isNotNull
    }
}