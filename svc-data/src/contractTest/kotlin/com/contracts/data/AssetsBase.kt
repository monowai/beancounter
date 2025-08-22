package com.contracts.data

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetResponse
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetService
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito
import org.springframework.core.io.ClassPathResource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.io.File
import java.util.Locale

/**
 * Asset Contract Tests. Called by Spring Cloud Contract Verifier
 */
class AssetsBase : ContractVerifierBase() {
    @MockitoBean
    private lateinit var assetService: AssetService

    @MockitoBean
    private lateinit var assetFinder: AssetFinder

    @BeforeEach
    fun mockAssets() {
        val kmiAsset =
            objectMapper
                .readValue(
                    ClassPathResource("contracts/assets/kmi-asset-by-id.json").file,
                    AssetResponse::class.java
                ).data

        Mockito
            .`when`(assetService.find("KMI"))
            .thenReturn(kmiAsset)

        Mockito
            .`when`(assetFinder.find("KMI"))
            .thenReturn(kmiAsset)

        Mockito
            .`when`(
                assetService.findOrCreate(
                    AssetInput(
                        "NASDAQ",
                        "NDAQ"
                    )
                )
            ).thenReturn(
                objectMapper
                    .readValue(
                        ClassPathResource("contracts/assets/ndaq-asset.json").file,
                        AssetResponse::class.java
                    ).data
            )
        mockAssets(assetService, assetFinder)
    }

    fun mockAssets(
        assetService: AssetService,
        assetFinder: AssetFinder
    ) {
        mockAssetCreateResponses(
            ClassPathResource("contracts/assets/nzd-cash-request.json").file,
            ClassPathResource("contracts/assets/nzd-cash-response.json").file,
            assetService,
            assetFinder
        )
        mockAssetCreateResponses(
            ClassPathResource("contracts/assets/usd-cash-request.json").file,
            ClassPathResource("contracts/assets/usd-cash-response.json").file,
            assetService,
            assetFinder
        )
        mockAssetCreateResponses(
            ClassPathResource("contracts/assets/create-request.json").file,
            ClassPathResource("contracts/assets/create-response.json").file,
            assetService,
            assetFinder
        )
        mockAssetCreateResponses(
            ClassPathResource("contracts/assets/ebay-request.json").file,
            ClassPathResource("contracts/assets/ebay-response.json").file,
            assetService,
            assetFinder
        )
        mockAssetCreateResponses(
            ClassPathResource("contracts/assets/msft-request.json").file,
            ClassPathResource("contracts/assets/msft-response.json").file,
            assetService,
            assetFinder
        )
        mockAssetCreateResponses(
            ClassPathResource("contracts/assets/bhp-asx-request.json").file,
            ClassPathResource("contracts/assets/bhp-asx-response.json").file,
            assetService,
            assetFinder
        )
        mockAssetCreateResponses(
            ClassPathResource("contracts/assets/bhp-lse-request.json").file,
            ClassPathResource("contracts/assets/bhp-lse-response.json").file,
            assetService,
            assetFinder
        )
        mockAssetCreateResponses(
            ClassPathResource("contracts/assets/abbv-request.json").file,
            ClassPathResource("contracts/assets/abbv-response.json").file,
            assetService,
            assetFinder
        )
        mockAssetCreateResponses(
            ClassPathResource("contracts/assets/amp-request.json").file,
            ClassPathResource("contracts/assets/amp-response.json").file,
            assetService,
            assetFinder
        )
    }

    private fun mockAssetCreateResponses(
        jsonRequest: File,
        jsonResponse: File,
        assetService: AssetService,
        assetFinder: AssetFinder
    ) {
        val assetRequest =
            objectMapper.readValue(
                jsonRequest,
                AssetRequest::class.java
            )
        val assetUpdateResponse =
            objectMapper.readValue(
                jsonResponse,
                AssetUpdateResponse::class.java
            )
        Mockito
            .`when`(assetService.handle(assetRequest))
            .thenReturn(assetUpdateResponse)

        val keys = assetUpdateResponse.data.keys
        for (key in keys) {
            val theAsset = assetUpdateResponse.data[key]
            theAsset!!.id
            Mockito.`when`(assetService.find(theAsset.id)).thenReturn(theAsset)
            Mockito
                .`when`(
                    assetFinder.findLocally(
                        AssetInput(
                            theAsset.market.code.uppercase(Locale.getDefault()),
                            theAsset.code.uppercase(Locale.getDefault())
                        )
                    )
                ).thenReturn(theAsset)
        }
    }
}