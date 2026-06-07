package com.beancounter.position.composite

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.Position
import com.beancounter.position.Constants.Companion.SGD
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class CompositeControllerTest {
    @Mock
    private lateinit var service: CompositeValuationService

    private val asset =
        Asset(
            code = "CPF",
            id = "cpf-asset-id",
            name = "CPF",
            market = Market("PRIVATE")
        )

    @Test
    fun `byId returns synthesised position when asset is composite`() {
        val controller = CompositeController(service)
        val position =
            Position(asset).apply {
                subAccounts["OA"] = BigDecimal("145000")
                subAccounts["SA"] = BigDecimal("78000")
                quantityValues.adjustment = BigDecimal("223000")
            }
        whenever(service.valueFor(eq(asset.id), any())).thenReturn(position)

        val response = controller.byId(asset.id, "today")

        assertThat(response.data).isNotNull
        assertThat(response.data!!.quantityValues.getTotal()).isEqualByComparingTo(BigDecimal("223000"))
    }

    @Test
    fun `byId returns null data when asset is not composite`() {
        val controller = CompositeController(service)
        whenever(service.valueFor(eq(asset.id), any())).thenReturn(null)

        val response = controller.byId(asset.id, "today")

        assertThat(response.data).isNull()
    }

    @Suppress("unused")
    private val sgd = SGD
}