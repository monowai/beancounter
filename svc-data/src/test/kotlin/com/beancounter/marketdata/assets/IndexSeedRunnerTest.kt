package com.beancounter.marketdata.assets

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.AssetCategory
import com.beancounter.marketdata.MarketDataBoot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest(classes = [MarketDataBoot::class])
@ActiveProfiles("h2db")
@AutoConfigureMockAuth
class IndexSeedRunnerTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var indexSeedRunner: IndexSeedRunner

    @Autowired
    private lateinit var assetFinder: AssetFinder

    @Autowired
    private lateinit var indexConfig: IndexConfig

    @Test
    fun `seed creates one asset per configured index`() {
        indexSeedRunner.seed()
        for (def in indexConfig.values) {
            val asset =
                assetFinder.findLocally(
                    AssetInput(market = INDEX_MARKET_CODE, code = def.code)
                )
            assertThat(asset)
                .describedAs("expected seeded index ${def.code}")
                .isNotNull
            assertThat(asset!!.assetCategory.id).isEqualTo(AssetCategory.INDEX)
            assertThat(asset.marketCode).isEqualTo(INDEX_MARKET_CODE)
        }
    }

    @Test
    fun `seed is idempotent across multiple invocations`() {
        indexSeedRunner.seed()
        val firstIds =
            indexConfig.values
                .mapNotNull { def ->
                    assetFinder
                        .findLocally(AssetInput(market = INDEX_MARKET_CODE, code = def.code))
                        ?.id
                }

        indexSeedRunner.seed()
        val secondIds =
            indexConfig.values
                .mapNotNull { def ->
                    assetFinder
                        .findLocally(AssetInput(market = INDEX_MARKET_CODE, code = def.code))
                        ?.id
                }

        assertThat(secondIds).isEqualTo(firstIds)
    }
}