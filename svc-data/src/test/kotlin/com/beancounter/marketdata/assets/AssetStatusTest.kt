package com.beancounter.marketdata.assets

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Status
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.SpringMvcDbTest
import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Tests for asset status management and filtering.
 */
@SpringMvcDbTest
@Transactional
class AssetStatusTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var assetRepository: AssetRepository

    @Autowired
    private lateinit var assetFinder: AssetFinder

    @Test
    fun `updateStatus should change asset status to Inactive`() {
        // Create an active asset
        val asset =
            assetService
                .handle(
                    AssetRequest(
                        mapOf(
                            "TEST" to
                                AssetInput(
                                    NASDAQ.code,
                                    "TEST",
                                    name = "Test Asset"
                                )
                        )
                    )
                ).data["TEST"]!!

        assertThat(asset.status).isEqualTo(Status.Active)

        // Deactivate the asset
        val updatedAsset = assetService.updateStatus(asset.id, Status.Inactive)

        assertThat(updatedAsset.status).isEqualTo(Status.Inactive)
        assertThat(updatedAsset.id).isEqualTo(asset.id)
    }

    @Test
    fun `findActiveAssetsForPricing should exclude inactive assets`() {
        // Create two assets - one active, one inactive
        val assets =
            assetService.handle(
                AssetRequest(
                    mapOf(
                        "ACTIVE" to
                            AssetInput(
                                NASDAQ.code,
                                "ACTIVE",
                                name = "Active Asset"
                            ),
                        "INACTIVE" to
                            AssetInput(
                                NASDAQ.code,
                                "INACTIVE",
                                name = "Inactive Asset"
                            )
                    )
                )
            )

        // Deactivate one asset
        assetService.updateStatus(assets.data["INACTIVE"]!!.id, Status.Inactive)

        // Verify only active asset is returned
        val codes = assetFinder.findActiveAssetsForPricing().map { it.code }
        assertThat(codes).contains("ACTIVE")
        assertThat(codes).doesNotContain("INACTIVE")
    }

    @Test
    fun `findActiveAssetsForPricing should exclude assets with empty code`() {
        // Create an asset with empty code directly in repository
        val emptyCodeAsset =
            assetRepository.save(
                com.beancounter.common.model.Asset(
                    code = "",
                    id = "empty-code-asset",
                    name = "Empty Code Asset",
                    market = NASDAQ,
                    marketCode = NASDAQ.code,
                    status = Status.Active
                )
            )

        // Also create a normal asset
        assetService.handle(
            AssetRequest(
                mapOf(
                    "NORMAL" to
                        AssetInput(
                            NASDAQ.code,
                            "NORMAL",
                            name = "Normal Asset"
                        )
                )
            )
        )

        // Verify empty code asset is excluded
        val activeAssets = assetFinder.findActiveAssetsForPricing()
        val ids = activeAssets.map { it.id }
        assertThat(ids).doesNotContain(emptyCodeAsset.id)
        assertThat(activeAssets.any { it.code == "NORMAL" }).isTrue()
    }

    @Test
    fun `findActiveAssetsForPricing should exclude private market assets`() {
        // Create a private market asset directly in repository
        val privateMarket =
            com.beancounter.common.model
                .Market("PRIVATE")
        val privateAsset =
            assetRepository.save(
                com.beancounter.common.model.Asset(
                    code = "PRIVATE-ASSET",
                    id = "private-asset-id",
                    name = "Private Investment",
                    market = privateMarket,
                    marketCode = "PRIVATE",
                    status = Status.Active
                )
            )

        // Also create a normal tradeable asset
        assetService.handle(
            AssetRequest(
                mapOf(
                    "TRADEABLE" to
                        AssetInput(
                            NASDAQ.code,
                            "TRADEABLE",
                            name = "Tradeable Asset"
                        )
                )
            )
        )

        // Verify private asset is excluded
        val pricingAssets = assetFinder.findActiveAssetsForPricing()
        val pricingIds = pricingAssets.map { it.id }
        assertThat(pricingIds).doesNotContain(privateAsset.id)
        assertThat(pricingAssets.any { it.code == "TRADEABLE" }).isTrue()
    }
}