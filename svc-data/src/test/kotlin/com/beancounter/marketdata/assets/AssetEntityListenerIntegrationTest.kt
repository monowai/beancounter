package com.beancounter.marketdata.assets

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Status
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

    @Test
    fun `Asset returned from save has market hydrated by @PostPersist or @PostUpdate listener`() {
        // Spring Data save() routes through em.merge() for application-assigned
        // ids; the returned managed instance has its `@Transient` fields cleared.
        // @PostPersist (initial insert) and @PostUpdate (subsequent updates)
        // re-populate them so callers do not need to call hydrateAsset manually.
        val asset =
            assetService
                .handle(
                    AssetRequest(
                        mapOf(
                            "SAVED" to
                                AssetInput(
                                    market = NASDAQ.code,
                                    code = "SAVED",
                                    name = "Saved Hydrated"
                                )
                        )
                    )
                ).data.values
                .first()

        // The initial save returns the @PostPersist-hydrated managed instance.
        assertThat(asset.market.code).isEqualTo(NASDAQ.code)
        assertThat(asset.market.currency).isNotNull
        assertThat(asset.assetCategory).isNotNull

        // Status change forces an @PostUpdate path via assetService.updateStatus,
        // which calls assetRepository.save on the modified copy.
        val updated = assetService.updateStatus(asset.id, Status.Inactive)

        assertThat(updated.status).isEqualTo(Status.Inactive)
        assertThat(updated.market.code).isEqualTo(NASDAQ.code)
        assertThat(updated.market.currency).isNotNull
        assertThat(updated.assetCategory).isNotNull
    }
}