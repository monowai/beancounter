package com.beancounter.marketdata.assets

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * A private asset exports under the code its owner reads, not the stored one.
 */
class AssetIoDefinitionTest {
    private val ownerId = "abcdefghijklmnopqrstuv"

    private fun privateAsset(code: String) =
        Asset(
            code = "$ownerId.$code",
            market = Market("PRIVATE"),
            category = "RE"
        )

    @Test
    fun `should export a private code without its owner prefix`() {
        assertThat(AssetIoDefinition().export(privateAsset("HOUSE"))[0]).isEqualTo("HOUSE")
    }

    @Test
    fun `should export a private dotted code whole`() {
        assertThat(AssetIoDefinition().export(privateAsset("BRK.B"))[0]).isEqualTo("BRK.B")
    }
}