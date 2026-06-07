package com.beancounter.position.composite

import com.beancounter.client.Assets
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.position.Constants.Companion.SGD
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class CompositeValuationServiceTest {
    private val asAt: LocalDate = LocalDate.of(2026, 6, 7)
    private val cpfAsset =
        Asset(
            code = "CPF",
            id = "cpf-asset-id",
            name = "CPF",
            market = Market("PRIVATE")
        )
    private val cpfConfig =
        PrivateAssetConfigDto(
            assetId = cpfAsset.id,
            policyType = "CPF",
            currency = SGD.code,
            subAccounts =
                listOf(
                    SubAccountDto("OA", balance = BigDecimal("145000")),
                    SubAccountDto("SA", balance = BigDecimal("78000"))
                )
        )

    @Test
    fun `valueFor returns null when asset is not composite`() {
        val configClient = mock<AssetConfigClient>()
        val assets = mock<Assets>()
        whenever(configClient.find(cpfAsset.id)).thenReturn(
            PrivateAssetConfigDto(assetId = cpfAsset.id, policyType = null)
        )

        val service =
            CompositeValuationService(
                configClient,
                assets,
                listOf(
                    CpfValuation(
                        com.beancounter.common.composite
                            .CompositeValuation()
                    )
                )
            )

        assertThat(service.valueFor(cpfAsset.id, asAt)).isNull()
    }

    @Test
    fun `valueFor returns null when no strategy supports policy type`() {
        val configClient = mock<AssetConfigClient>()
        val assets = mock<Assets>()
        whenever(configClient.find(cpfAsset.id)).thenReturn(
            PrivateAssetConfigDto(assetId = cpfAsset.id, policyType = "ILP")
        )

        val service =
            CompositeValuationService(
                configClient,
                assets,
                listOf(
                    CpfValuation(
                        com.beancounter.common.composite
                            .CompositeValuation()
                    )
                )
            )

        assertThat(service.valueFor(cpfAsset.id, asAt)).isNull()
    }

    @Test
    fun `valueFor delegates to matching strategy and returns synthesised position`() {
        val configClient = mock<AssetConfigClient>()
        val assets = mock<Assets>()
        whenever(configClient.find(cpfAsset.id)).thenReturn(cpfConfig)
        whenever(assets.find(cpfAsset.id)).thenReturn(cpfAsset)

        val service =
            CompositeValuationService(
                configClient,
                assets,
                listOf(
                    CpfValuation(
                        com.beancounter.common.composite
                            .CompositeValuation()
                    )
                )
            )

        val position = service.valueFor(cpfAsset.id, asAt)

        assertThat(position).isNotNull
        assertThat(position!!.asset).isEqualTo(cpfAsset)
        assertThat(position.quantityValues.getTotal()).isEqualByComparingTo(BigDecimal("223000"))
    }
}