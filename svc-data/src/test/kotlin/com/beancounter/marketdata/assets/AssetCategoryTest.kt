package com.beancounter.marketdata.assets

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.marketdata.SpringMvcDbTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringMvcDbTest
@AutoConfigureMockAuth
internal class AssetCategoryTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

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