package com.beancounter.common

import com.beancounter.common.contracts.PortfolioResponse
import com.beancounter.common.contracts.PortfoliosRequest
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolioInput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Portfolio Pojo Tests.
 */
class TestPortfolio {
    private val objectMapper = BcJson().objectMapper

    @Test
    @Throws(Exception::class)
    fun is_PortfolioResultsSerializing() {
        val portfolioInput = getPortfolioInput("Test")
        val portfolioInputs: MutableCollection<PortfolioInput> = ArrayList()
        portfolioInputs.add(portfolioInput)
        val portfoliosRequest = PortfoliosRequest(portfolioInputs)
        var json = objectMapper.writeValueAsString(portfoliosRequest)
        assertThat(objectMapper.readValue(json, PortfoliosRequest::class.java))
            .usingRecursiveComparison().isEqualTo(portfoliosRequest)
        val portfolios: MutableCollection<Portfolio> = ArrayList()
        portfolios.add(getPortfolio("TEST"))
        val portfoliosResponse = PortfoliosResponse(portfolios)
        json = objectMapper.writeValueAsString(portfoliosResponse)
        val fromJson = objectMapper.readValue(json, PortfoliosResponse::class.java).data
        assertThat(fromJson).hasSize(portfoliosResponse.data.size)
        assertThat(fromJson.iterator().next())
            .usingRecursiveComparison().isEqualTo(portfolios.iterator().next())
    }

    @Test
    fun is_PortfolioResponseSerializing() {
        val portfolioResponse = PortfolioResponse(getPortfolio("TEST"))
        val json = objectMapper.writeValueAsString(portfolioResponse)
        assertThat(objectMapper.readValue(json, PortfolioResponse::class.java).data)
            .usingRecursiveComparison().isEqualTo(portfolioResponse.data)
    }

    @Test
    fun is_PortfoliosResponseSerializing() {

        val portfolios = ArrayList<Portfolio>()
        val portfolio = getPortfolio("TEST")
        portfolio.owner = SystemUser(
            "id",
            "email",
            false,
            LocalDate.now().minusDays(5)
        )
        portfolios.add(portfolio)
        val portfoliosResponse = PortfoliosResponse(portfolios)
        val json = objectMapper.writeValueAsString(portfoliosResponse)
        val fromJson = objectMapper.readValue(json, PortfoliosResponse::class.java)
        assertThat(fromJson.data).isNotNull.hasSize(1)
        assertThat(fromJson.data.iterator().next()).isEqualTo(portfolio)
    }
}
