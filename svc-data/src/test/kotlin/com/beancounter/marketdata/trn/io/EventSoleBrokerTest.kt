package com.beancounter.marketdata.trn.io

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.Registration
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Broker
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.broker.BrokerRepository
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.trn.TrnRepository
import com.beancounter.marketdata.trn.settlement.TrnSettlementService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.LocalDate

/**
 * A dividend or split from svc-event carries no broker. When the portfolio holds the asset at
 * one broker only, that broker is the one it belongs to.
 */
@SpringMvcDbTest
class EventSoleBrokerTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var trnImportService: TrnImportService

    @Autowired
    private lateinit var trnSettlementService: TrnSettlementService

    @Autowired
    private lateinit var trnRepository: TrnRepository

    @Autowired
    private lateinit var brokerRepository: BrokerRepository

    @Autowired
    private lateinit var portfolioService: PortfolioService

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var systemUserService: Registration

    private val held = LocalDate.of(2024, 1, 2)
    private val paid = LocalDate.of(2024, 6, 3)

    private fun login(name: String): SystemUser {
        val email = "esb-$name@test.com"
        val user = SystemUser(id = email, email = email)
        mockAuthConfig.login(user, systemUserService)
        return user
    }

    private fun portfolio(code: String): Portfolio =
        portfolioService.save(listOf(PortfolioInput(code = code, name = code, currency = USD.code))).first()

    private fun asset(code: String): Asset =
        assetService.handle(AssetRequest(AssetInput(NASDAQ.code, code), code)).data.getValue(code)

    private fun added(
        portfolio: Portfolio,
        asset: Asset,
        broker: Broker?
    ) {
        trnRepository.save(
            Trn(
                trnType = TrnType.ADD,
                asset = asset,
                quantity = BigDecimal("10"),
                portfolio = portfolio,
                broker = broker,
                tradeDate = held,
                status = TrnStatus.SETTLED
            )
        )
    }

    private fun dividend(
        portfolio: Portfolio,
        asset: Asset,
        ref: String
    ): Trn =
        trnImportService
            .fromTrnRequest(
                TrustedTrnEvent(
                    portfolio,
                    trnInput =
                        TrnInput(
                            callerRef = CallerRef("ALPHA", ref),
                            assetId = asset.id,
                            trnType = TrnType.DIVI,
                            quantity = BigDecimal("10"),
                            tradeDate = paid,
                            tradeAmount = BigDecimal("5"),
                            price = BigDecimal("0.5"),
                            tradeCurrency = USD.code,
                            cashCurrency = USD.code,
                            status = TrnStatus.PROPOSED
                        )
                )
            ).single()

    @Test
    fun `should give a dividend the only broker the portfolio holds the asset at`() {
        val owner = login("sole")
        val portfolio = portfolio("ESB-SOLE")
        val broker = brokerRepository.save(Broker(name = "Sole Broker", owner = owner))
        val asset = asset("ESBA")
        added(portfolio, asset, broker)

        assertThat(dividend(portfolio, asset, "esb-sole").broker?.id).isEqualTo(broker.id)
    }

    @Test
    fun `should leave the broker empty when the asset is held at two brokers`() {
        val owner = login("two")
        val portfolio = portfolio("ESB-TWO")
        val asset = asset("ESBB")
        added(portfolio, asset, brokerRepository.save(Broker(name = "First Broker", owner = owner)))
        added(portfolio, asset, brokerRepository.save(Broker(name = "Second Broker", owner = owner)))

        assertThat(dividend(portfolio, asset, "esb-two").broker).isNull()
    }

    @Test
    fun `should leave the broker empty when some of the holding has no broker`() {
        val owner = login("mixed")
        val portfolio = portfolio("ESB-MIXED")
        val asset = asset("ESBC")
        added(portfolio, asset, brokerRepository.save(Broker(name = "Mixed Broker", owner = owner)))
        added(portfolio, asset, null)

        assertThat(dividend(portfolio, asset, "esb-mixed").broker).isNull()
    }

    @Test
    fun `should give a broker-less proposed dividend the sole broker when it is settled`() {
        val owner = login("settle")
        val portfolio = portfolio("ESB-SETTLE")
        val broker = brokerRepository.save(Broker(name = "Settle Broker", owner = owner))
        val asset = asset("ESBD")
        added(portfolio, asset, broker)
        val proposed =
            trnRepository.save(
                Trn(
                    trnType = TrnType.DIVI,
                    asset = asset,
                    quantity = BigDecimal("10"),
                    price = BigDecimal("0.5"),
                    tradeAmount = BigDecimal("5"),
                    portfolio = portfolio,
                    tradeDate = paid,
                    status = TrnStatus.PROPOSED
                )
            )

        val settled = trnSettlementService.settle(portfolio, proposed)

        assertThat(settled?.broker?.id).isEqualTo(broker.id)
    }
}