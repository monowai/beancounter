package com.beancounter.marketdata.assets

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.input.AssetInput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
internal class AssetServiceCustomTest {
    @Autowired
    private lateinit var assetService: AssetService
    @Test
    fun is_CustomAsset() {
        val customAsset = AssetInput("Custom", "House")
        val asset = assetService.handle(AssetRequest(customAsset))
        assertThat(asset).isNotNull
    }
}
