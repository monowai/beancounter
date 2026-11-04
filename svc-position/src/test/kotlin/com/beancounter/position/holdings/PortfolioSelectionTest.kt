package com.beancounter.position.holdings

import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.exception.NotFoundException
import com.beancounter.position.utils.TestHelpers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class PortfolioSelectionTest {
    @Test
    fun `codes drop blank entries the way ids do`() {
        val named = TestHelpers.createTestPortfolio("named")
        val blankCode = TestHelpers.createTestPortfolio("blank").copy(code = "")
        val client: PortfolioServiceClient = mock()
        whenever(client.portfolios).thenReturn(PortfoliosResponse(listOf(named, blankCode)))

        val selected = PortfolioSelection(codes = "${named.code}, ,").resolve(client)

        assertThat(selected).containsExactly(named)
    }

    @Test
    fun `ids skip a portfolio the caller cannot see`() {
        val visible = TestHelpers.createTestPortfolio("visible")
        val client: PortfolioServiceClient = mock()
        whenever(client.getPortfolioById(visible.id)).thenReturn(visible)
        whenever(client.getPortfolioById("hidden")).thenThrow(NotFoundException("not found"))

        val selected = PortfolioSelection(ids = "${visible.id},hidden").resolve(client)

        assertThat(selected).containsExactly(visible)
    }
}