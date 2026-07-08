package com.beancounter.marketdata.trn

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.Registration
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Broker
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.broker.BrokerRepository
import com.beancounter.marketdata.portfolio.PortfolioService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Reverse broker lookup for an asset — `GET /trns/asset/{assetId}/brokers` —
 * used by the frontend's WeightedSellDialog to show which broker(s) hold a
 * given asset before staging a weighted sell proposal.
 */
@SpringMvcDbTest
class TrnAssetBrokerHoldingsTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var trnBrokerService: TrnBrokerService

    @Autowired
    private lateinit var brokerRepository: BrokerRepository

    @Autowired
    private lateinit var portfolioService: PortfolioService

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var trnRepository: TrnRepository

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var systemUserService: Registration

    private fun login(email: String): SystemUser {
        val user = SystemUser(id = email, email = email)
        mockAuthConfig.login(user, systemUserService)
        return user
    }

    private fun portfolio(code: String): Portfolio =
        portfolioService
            .save(listOf(PortfolioInput(code = code, name = code, currency = USD.code)))
            .first()

    private fun asset(code: String): Asset =
        assetService
            .handle(AssetRequest(AssetInput(NASDAQ.code, code), code))
            .data[code]!!

    private fun trade(
        trnType: TrnType,
        portfolio: Portfolio,
        broker: Broker?,
        asset: Asset,
        quantity: String,
        tradeDate: LocalDate = LocalDate.of(2026, 1, 1)
    ) {
        trnRepository.save(
            Trn(
                trnType = trnType,
                asset = asset,
                quantity = BigDecimal(quantity),
                portfolio = portfolio,
                broker = broker,
                tradeDate = tradeDate
            )
        )
    }

    @Test
    fun `asset held at two brokers returns two entries with correct per-broker quantities`() {
        val user = login("abh-two-brokers@test.com")
        val brokerA = brokerRepository.save(Broker(name = "ABH-BROKER-A", owner = user))
        val brokerB = brokerRepository.save(Broker(name = "ABH-BROKER-B", owner = user))
        val p1 = portfolio("ABH-P1")
        val p2 = portfolio("ABH-P2")
        val asset = asset("ABHA1")
        trade(TrnType.BUY, p1, brokerA, asset, "100")
        trade(TrnType.BUY, p2, brokerB, asset, "60")

        val response = trnBrokerService.getAssetBrokerHoldings(asset.id)

        assertThat(response.data).hasSize(2)
        val byBroker = response.data.associateBy { it.brokerId }
        assertThat(byBroker[brokerA.id]!!.brokerName).isEqualTo("ABH-BROKER-A")
        assertThat(byBroker[brokerA.id]!!.holding.quantity).isEqualByComparingTo("100")
        assertThat(byBroker[brokerA.id]!!.holding.portfolioGroups).hasSize(1)
        assertThat(
            byBroker[brokerA.id]!!
                .holding.portfolioGroups
                .first()
                .portfolioId
        ).isEqualTo(p1.id)

        assertThat(byBroker[brokerB.id]!!.brokerName).isEqualTo("ABH-BROKER-B")
        assertThat(byBroker[brokerB.id]!!.holding.quantity).isEqualByComparingTo("60")
        assertThat(byBroker[brokerB.id]!!.holding.portfolioGroups).hasSize(1)
        assertThat(
            byBroker[brokerB.id]!!
                .holding.portfolioGroups
                .first()
                .portfolioId
        ).isEqualTo(p2.id)
    }

    @Test
    fun `broker with net-zero position for the asset is excluded`() {
        val user = login("abh-net-zero-broker@test.com")
        val brokerZero = brokerRepository.save(Broker(name = "ABH-BROKER-ZERO", owner = user))
        val brokerHeld = brokerRepository.save(Broker(name = "ABH-BROKER-HELD", owner = user))
        val p1 = portfolio("ABH-NZ-P1")
        val p2 = portfolio("ABH-NZ-P2")
        val asset = asset("ABHA2")
        // brokerZero: bought and fully sold -> net zero
        trade(TrnType.BUY, p1, brokerZero, asset, "50", LocalDate.of(2026, 1, 1))
        trade(TrnType.SELL, p1, brokerZero, asset, "50", LocalDate.of(2026, 2, 1))
        // brokerHeld: still holds
        trade(TrnType.BUY, p2, brokerHeld, asset, "20")

        val response = trnBrokerService.getAssetBrokerHoldings(asset.id)

        assertThat(response.data).hasSize(1)
        assertThat(response.data.first().brokerId).isEqualTo(brokerHeld.id)
    }

    @Test
    fun `zero-quantity portfolio group within a broker is excluded`() {
        val user = login("abh-zero-portfolio-group@test.com")
        val broker = brokerRepository.save(Broker(name = "ABH-BROKER-MIXED", owner = user))
        val soldOutPortfolio = portfolio("ABH-ZG-P1")
        val heldPortfolio = portfolio("ABH-ZG-P2")
        val asset = asset("ABHA3")
        // soldOutPortfolio: bought and fully sold at this broker -> zero group
        trade(TrnType.BUY, soldOutPortfolio, broker, asset, "30", LocalDate.of(2026, 1, 1))
        trade(TrnType.SELL, soldOutPortfolio, broker, asset, "30", LocalDate.of(2026, 2, 1))
        // heldPortfolio: still holds at the same broker -> broker net quantity nonzero
        trade(TrnType.BUY, heldPortfolio, broker, asset, "15")

        val response = trnBrokerService.getAssetBrokerHoldings(asset.id)

        assertThat(response.data).hasSize(1)
        val holding = response.data.first().holding
        assertThat(holding.quantity).isEqualByComparingTo("15")
        assertThat(holding.portfolioGroups).hasSize(1)
        assertThat(holding.portfolioGroups.first().portfolioId).isEqualTo(heldPortfolio.id)
    }

    @Test
    fun `another user's trns are invisible`() {
        val owner = login("abh-owner@test.com")
        val broker = brokerRepository.save(Broker(name = "ABH-BROKER-OWNER", owner = owner))
        val ownedPortfolio = portfolio("ABH-OWN-P1")
        val asset = asset("ABHA4")
        trade(TrnType.BUY, ownedPortfolio, broker, asset, "10")

        // Switch to a different user and reuse the same asset code — a different
        // system user cannot see the first user's broker/portfolio/trn.
        login("abh-other@test.com")

        val response = trnBrokerService.getAssetBrokerHoldings(asset.id)

        assertThat(response.data).isEmpty()
    }

    @Test
    fun `split adjustment is reflected in per-broker quantities`() {
        val user = login("abh-split@test.com")
        val broker = brokerRepository.save(Broker(name = "ABH-BROKER-SPLIT", owner = user))
        val p1 = portfolio("ABH-SPLIT-P1")
        val asset = asset("ABHA5")
        // BUY 12, 4:1 SPLIT (no broker), SELL post-split shares
        trade(TrnType.BUY, p1, broker, asset, "12", LocalDate.of(2026, 1, 1))
        trade(TrnType.SPLIT, p1, null, asset, "4", LocalDate.of(2026, 4, 21))
        trade(TrnType.SELL, p1, broker, asset, "40", LocalDate.of(2026, 6, 23))

        val response = trnBrokerService.getAssetBrokerHoldings(asset.id)

        assertThat(response.data).hasSize(1)
        // 12 -> *4 = 48, sell 40 -> net 8
        assertThat(
            response.data
                .first()
                .holding.quantity
        ).isEqualByComparingTo("8")
    }

    @Test
    fun `no-broker trns are grouped under the NO_BROKER sentinel`() {
        val user = login("abh-no-broker@test.com")
        val p1 = portfolio("ABH-NB-P1")
        val asset = asset("ABHA6")
        trade(TrnType.BUY, p1, null, asset, "25")

        val response = trnBrokerService.getAssetBrokerHoldings(asset.id)

        assertThat(response.data).hasSize(1)
        val entry = response.data.first()
        assertThat(entry.brokerId).isEqualTo(TrnBrokerService.NO_BROKER)
        assertThat(entry.brokerName).isEqualTo("No Broker")
        assertThat(entry.holding.quantity).isEqualByComparingTo("25")
    }

    @Test
    fun `unknown asset returns an empty response`() {
        login("abh-unknown-asset@test.com")

        val response = trnBrokerService.getAssetBrokerHoldings("does-not-exist")

        assertThat(response.data).isEmpty()
    }
}