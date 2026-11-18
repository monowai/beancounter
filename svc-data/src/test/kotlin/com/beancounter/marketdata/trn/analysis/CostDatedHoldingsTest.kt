package com.beancounter.marketdata.trn.analysis

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.Registration
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.trn.TrnQueryService
import com.beancounter.marketdata.trn.TrnRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate

/**
 * svc-event asks which imported holdings (ADDs with a cost date) are owed a dividend recorded
 * on a date: those with costDate <= recordDate < tradeDate, in portfolios the caller can view.
 */
@SpringMvcDbTest
class CostDatedHoldingsTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var trnQueryService: TrnQueryService

    @Autowired
    private lateinit var trnRepository: TrnRepository

    @Autowired
    private lateinit var portfolioService: PortfolioService

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var systemUserService: Registration

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val costDate = LocalDate.of(2016, 1, 4)
    private val tradeDate = LocalDate.of(2026, 11, 18)

    private fun login(name: String): Jwt {
        val email = "cdh-$name@test.com"
        return mockAuthConfig.login(SystemUser(id = email, email = email), systemUserService)
    }

    private fun portfolio(code: String): Portfolio =
        portfolioService.save(listOf(PortfolioInput(code = code, name = code, currency = USD.code))).first()

    private fun asset(code: String): Asset =
        assetService.handle(AssetRequest(AssetInput(NASDAQ.code, code), code)).data.getValue(code)

    private fun added(
        portfolio: Portfolio,
        asset: Asset,
        costDate: LocalDate?
    ): Trn =
        trnRepository.save(
            Trn(
                trnType = TrnType.ADD,
                asset = asset,
                quantity = BigDecimal.TEN,
                portfolio = portfolio,
                tradeDate = tradeDate,
                costDate = costDate,
                status = TrnStatus.SETTLED
            )
        )

    private fun owed(
        asset: Asset,
        recordDate: String
    ) = trnQueryService.findCostDatedAdds(asset.id, LocalDate.parse(recordDate)).map { it.id }

    @Test
    fun `should find an imported holding owed a dividend recorded between its cost and trade dates`() {
        login("owed")
        val asset = asset("CDHA")
        val add = added(portfolio("CDH-OWED"), asset, costDate)

        assertThat(owed(asset, "2016-01-04")).containsExactly(add.id)
        assertThat(owed(asset, "2019-05-10")).containsExactly(add.id)
        assertThat(owed(asset, "2026-11-17")).containsExactly(add.id)
        assertThat(owed(asset, "2016-01-03")).isEmpty()
        assertThat(owed(asset, "2026-11-18")).isEmpty()
    }

    @Test
    fun `should not find an ADD without a cost date`() {
        login("undated")
        val asset = asset("CDHB")
        added(portfolio("CDH-UNDATED"), asset, null)

        assertThat(owed(asset, "2019-05-10")).isEmpty()
    }

    @Test
    fun `should not find an imported holding in a portfolio the caller cannot view`() {
        login("owner")
        val asset = asset("CDHC")
        added(portfolio("CDH-OWNER"), asset, costDate)

        login("stranger")

        assertThat(owed(asset, "2019-05-10")).isEmpty()
    }

    @Test
    fun `should answer the imported holdings over the api`() {
        val token = login("api")
        val asset = asset("CDHD")
        val add = added(portfolio("CDH-API"), asset, costDate)

        val body =
            mockMvc
                .perform(get("/trns/asset/${asset.id}/cost-dated?asAt=2019-05-10").with(jwt().jwt(token)))
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString
        val trns = objectMapper.readValue(body, TrnResponse::class.java).data.trns

        assertThat(trns.map { it.id }).containsExactly(add.id)
        assertThat(trns.single().costDate).isEqualTo(costDate)
    }
}