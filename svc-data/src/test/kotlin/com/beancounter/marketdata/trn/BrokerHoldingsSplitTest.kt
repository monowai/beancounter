package com.beancounter.marketdata.trn

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.Registration
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.Broker
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
 * Broker-scoped holdings must apply corporate-action SPLIT transactions even though
 * those rows carry no broker (broker_id is null). Otherwise a position that was sold
 * out at the broker reads negative: the split that grew the holding is silently dropped.
 *
 * Repro (real incident): VO at DBS — BUY 12, a 4:1 SPLIT (no broker), SELL of the
 * post-split shares. Without the split the broker view computes BUY - SELL and goes
 * negative.
 */
@SpringMvcDbTest
class BrokerHoldingsSplitTest {
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

    private fun seedSplitScenario(
        user: SystemUser,
        brokerName: String,
        portfolioCode: String,
        assetCode: String,
        sellQty: String
    ): Broker {
        val broker = brokerRepository.save(Broker(name = brokerName, owner = user))
        val portfolio =
            portfolioService
                .save(
                    listOf(
                        PortfolioInput(
                            code = portfolioCode,
                            name = portfolioCode,
                            currency = USD.code
                        )
                    )
                ).first()
        val asset =
            assetService
                .handle(
                    AssetRequest(
                        AssetInput(NASDAQ.code, assetCode),
                        assetCode
                    )
                ).data[assetCode]!!

        // BUY 12 at the broker
        trnRepository.save(
            Trn(
                trnType = TrnType.BUY,
                asset = asset,
                quantity = BigDecimal("12"),
                portfolio = portfolio,
                broker = broker,
                tradeDate = LocalDate.of(2026, 1, 1)
            )
        )
        // 4:1 SPLIT — corporate action, carries NO broker
        trnRepository.save(
            Trn(
                trnType = TrnType.SPLIT,
                asset = asset,
                quantity = BigDecimal("4"),
                portfolio = portfolio,
                broker = null,
                tradeDate = LocalDate.of(2026, 4, 21)
            )
        )
        // SELL post-split shares at the broker
        trnRepository.save(
            Trn(
                trnType = TrnType.SELL,
                asset = asset,
                quantity = BigDecimal(sellQty),
                portfolio = portfolio,
                broker = broker,
                tradeDate = LocalDate.of(2026, 6, 23)
            )
        )
        return broker
    }

    @Test
    fun `broker holdings apply broker-null split so position is not negative`() {
        val user = SystemUser(id = "broker-split-user", email = "broker-split@test.com")
        mockAuthConfig.login(user, systemUserService)

        // 12 shares, 4:1 split -> 48, sell 40 -> net 8 (not 12 - 40 = -28)
        val broker = seedSplitScenario(user, "DBS", "BSPLIT-P1", "BSPL", sellQty = "40")

        val holdings = trnBrokerService.getBrokerHoldings(broker.id)

        val position = holdings.holdings.firstOrNull { it.assetCode == "BSPL" }
        assertThat(position)
            .describedAs("split-adjusted position must be present and positive")
            .isNotNull
        assertThat(position!!.quantity).isEqualByComparingTo("8")
    }

    @Test
    fun `findForBroker includes broker-null split for downstream accumulation`() {
        val user = SystemUser(id = "broker-split-pos-user", email = "broker-split-pos@test.com")
        mockAuthConfig.login(user, systemUserService)

        val broker = seedSplitScenario(user, "IBKR", "BSPLIT-P2", "BSPL", sellQty = "48")

        val trns = trnBrokerService.findForBroker(broker.id, LocalDate.of(2026, 6, 23))

        assertThat(trns.map { it.trnType })
            .describedAs("split must be returned so the Accumulator can apply it")
            .contains(TrnType.SPLIT)
    }
}