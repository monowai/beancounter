package com.beancounter.marketdata.registration

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.Registration
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Broker
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetRepository
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.broker.BrokerRepository
import com.beancounter.marketdata.broker.BrokerSettlementAccount
import com.beancounter.marketdata.broker.BrokerSettlementAccountRepository
import com.beancounter.marketdata.portfolio.PortfolioRepository
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.trn.TrnRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal

/**
 * The two offboarding cascades, [PortfolioErasure] and [OwnedAssetErasure],
 * against real state: counts and erasures are scoped to one owner and run in
 * FK order.
 */
@SpringMvcDbTest
class OffboardingErasureTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var portfolioErasure: PortfolioErasure

    @Autowired
    private lateinit var ownedAssetErasure: OwnedAssetErasure

    @Autowired
    private lateinit var portfolioService: PortfolioService

    @Autowired
    private lateinit var portfolioRepository: PortfolioRepository

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var assetRepository: AssetRepository

    @Autowired
    private lateinit var trnRepository: TrnRepository

    @Autowired
    private lateinit var brokerRepository: BrokerRepository

    @Autowired
    private lateinit var brokerSettlementAccountRepository: BrokerSettlementAccountRepository

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var registration: Registration

    private fun login(id: String): SystemUser {
        val user = SystemUser(id = id, email = "$id@test.com")
        mockAuthConfig.login(user, registration)
        return user
    }

    private fun ownedAsset(
        owner: SystemUser,
        code: String
    ): Asset =
        assetService
            .handle(
                AssetRequest(
                    mapOf(
                        code to
                            AssetInput.toRealEstate(
                                currency = USD,
                                code = code,
                                name = code,
                                owner = owner.id
                            )
                    )
                )
            ).data
            .getValue(code)

    @Test
    fun `should count and erase only the owner's portfolios with their trns`() {
        val other = login("erase-pf-other")
        val kept =
            portfolioService
                .save(listOf(PortfolioInput(code = "ERASE-PF-KEPT", name = "Kept", currency = USD.code)))
                .first()
        val owner = login("erase-pf-owner")
        val portfolios =
            portfolioService.save(
                listOf(
                    PortfolioInput(code = "ERASE-PF-A", name = "A", currency = USD.code),
                    PortfolioInput(code = "ERASE-PF-B", name = "B", currency = USD.code)
                )
            )
        val trn =
            trnRepository.save(
                Trn(
                    trnType = TrnType.BUY,
                    asset = ownedAsset(owner, "ERASE-PF-HOUSE"),
                    quantity = BigDecimal.ONE,
                    portfolio = portfolios.first()
                )
            )

        assertThat(portfolioErasure.count(owner)).isEqualTo(2)

        assertThat(portfolioErasure.erase(owner)).isEqualTo(2)
        assertThat(portfolioErasure.count(owner)).isEqualTo(0)
        assertThat(trnRepository.findById(trn.id)).isEmpty()
        assertThat(portfolioRepository.findById(kept.id)).isPresent()
        assertThat(portfolioErasure.count(other)).isEqualTo(1)
    }

    @Test
    fun `should count and erase only the owner's assets and brokers when settlement accounts link them`() {
        val other = login("erase-asset-other")
        val keptBroker = brokerRepository.save(Broker(name = "Kept Broker", owner = other))
        val othersCash = ownedAsset(other, "ERASE-ASSET-OTHER-CASH")
        val owner = login("erase-asset-owner")
        val cash = ownedAsset(owner, "ERASE-ASSET-CASH")
        ownedAsset(owner, "ERASE-ASSET-HOUSE")
        val broker = brokerRepository.save(Broker(name = "Erased Broker", owner = owner))
        brokerSettlementAccountRepository.save(
            BrokerSettlementAccount(broker = broker, currencyCode = USD.code, account = cash)
        )
        // A settlement account on the owner's broker whose cash asset is not the owner's:
        // only the owner-wide settlement clear removes it before the broker delete.
        brokerSettlementAccountRepository.save(
            BrokerSettlementAccount(broker = broker, currencyCode = NZD.code, account = othersCash)
        )

        assertThat(ownedAssetErasure.count(owner)).isEqualTo(OwnedAssets(assets = 2, brokers = 1))

        assertThat(ownedAssetErasure.erase(owner)).isEqualTo(OwnedAssets(assets = 2, brokers = 1))
        assertThat(ownedAssetErasure.count(owner)).isEqualTo(OwnedAssets(assets = 0, brokers = 0))
        assertThat(assetRepository.findBySystemUserId(owner.id)).isEmpty()
        assertThat(brokerRepository.findById(keptBroker.id)).isPresent()
        assertThat(assetRepository.findById(othersCash.id)).isPresent()
    }
}