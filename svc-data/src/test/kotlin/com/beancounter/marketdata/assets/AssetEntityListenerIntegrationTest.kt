package com.beancounter.marketdata.assets

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.SpringMvcDbTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Locks the contract that an Asset loaded from JPA has its transient `market`
 * and `assetCategory` fields populated without callers having to invoke
 * `AssetFinder.hydrateAsset` first.
 *
 * Driven by a Hibernate @PostLoad EntityListener registered via
 * META-INF/orm.xml so jar-common stays free of svc-data dependencies.
 */
@SpringMvcDbTest
class AssetEntityListenerIntegrationTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    lateinit var assetService: AssetService

    @Autowired
    lateinit var assetRepository: AssetRepository

    @Test
    fun `Asset loaded via repository has market hydrated by @PostLoad listener`() {
        val asset =
            assetService
                .handle(
                    AssetRequest(
                        mapOf(
                            "AUTO" to
                                AssetInput(
                                    market = NASDAQ.code,
                                    code = "AUTO",
                                    name = "Auto Hydrated"
                                )
                        )
                    )
                ).data.values
                .first()

        // Repository load — must go through @PostLoad
        val reloaded = assetRepository.findById(asset.id).orElseThrow()

        assertThat(reloaded.marketCode).isEqualTo(NASDAQ.code)
        assertThat(reloaded.market.code).isEqualTo(NASDAQ.code)
        assertThat(reloaded.market.currency).isNotNull
        assertThat(reloaded.assetCategory).isNotNull
    }
}