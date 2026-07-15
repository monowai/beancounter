package com.beancounter.marketdata.trn

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.Registration
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.Broker
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AccountingTypeService
import com.beancounter.marketdata.assets.AssetRepository
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.broker.BrokerRepository
import com.beancounter.marketdata.broker.BrokerSettlementAccount
import com.beancounter.marketdata.broker.BrokerSettlementAccountRepository
import com.beancounter.marketdata.portfolio.PortfolioService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Weighted PROPOSED SELL proposals generated from the broker reconciliation view —
 * one trn per portfolio holding the asset at the broker, sized as weight * that
 * portfolio's split-adjusted broker holding.
 */
@SpringMvcDbTest
class TrnBrokerServiceProposeTest {
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
    private lateinit var assetRepository: AssetRepository

    @Autowired
    private lateinit var accountingTypeService: AccountingTypeService

    @Autowired
    private lateinit var trnRepository: TrnRepository

    @Autowired
    private lateinit var brokerSettlementAccountRepository: BrokerSettlementAccountRepository

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

    private fun asset(code: String) =
        assetService
            .handle(AssetRequest(AssetInput(NASDAQ.code, code), code))
            .data[code]!!

    private fun assetWithLot(
        code: String,
        boardLot: Int
    ): com.beancounter.common.model.Asset {
        val asset = asset(code)
        asset.accountingType =
            accountingTypeService.getOrCreate(
                category = "LOT$boardLot",
                currency = USD,
                boardLot = boardLot
            )
        return assetRepository.save(asset)
    }

    private fun buy(
        portfolio: Portfolio,
        broker: Broker,
        asset: com.beancounter.common.model.Asset,
        quantity: String,
        tradeDate: LocalDate = LocalDate.of(2026, 1, 1)
    ) {
        trnRepository.save(
            Trn(
                trnType = TrnType.BUY,
                asset = asset,
                quantity = BigDecimal(quantity),
                portfolio = portfolio,
                broker = broker,
                tradeDate = tradeDate
            )
        )
    }

    private fun sell(
        portfolio: Portfolio,
        broker: Broker,
        asset: com.beancounter.common.model.Asset,
        quantity: String,
        tradeDate: LocalDate = LocalDate.of(2026, 2, 1)
    ) {
        trnRepository.save(
            Trn(
                trnType = TrnType.SELL,
                asset = asset,
                quantity = BigDecimal(quantity),
                portfolio = portfolio,
                broker = broker,
                tradeDate = tradeDate
            )
        )
    }

    @Test
    fun `should create one proposed SELL per portfolio holding the asset at the broker weighted by holding`() {
        val user = login("propose-weighted@test.com")
        val broker = brokerRepository.save(Broker(name = "PW-BROKER1", owner = user))
        val p1 = portfolio("PW-P1")
        val p2 = portfolio("PW-P2")
        val asset = asset("PWA1")
        buy(p1, broker, asset, "100")
        buy(p2, broker, asset, "60")

        val response =
            trnBrokerService.proposeWeighted(
                broker.id,
                BrokerProposalRequest(
                    assetId = asset.id,
                    weight = BigDecimal("0.5"),
                    price = BigDecimal("10.00")
                )
            )

        val trns = response.data.trns
        assertThat(trns).hasSize(2)
        assertThat(trns).allSatisfy { trn ->
            assertThat(trn.trnType).isEqualTo(TrnType.SELL)
            assertThat(trn.status).isEqualTo(TrnStatus.PROPOSED)
            assertThat(trn.brokerId).isEqualTo(broker.id)
            assertThat(trn.price).isEqualByComparingTo("10.00")
        }
        val byPortfolio = trns.associateBy { it.portfolioId }
        assertThat(byPortfolio[p1.id]!!.quantity).isEqualByComparingTo("50")
        assertThat(byPortfolio[p1.id]!!.tradeAmount).isEqualByComparingTo("500.00")
        assertThat(byPortfolio[p2.id]!!.quantity).isEqualByComparingTo("30")
        assertThat(byPortfolio[p2.id]!!.tradeAmount).isEqualByComparingTo("300.00")
    }

    @Test
    fun `should reject price not greater than zero`() {
        val user = login("propose-zero-price@test.com")
        val broker = brokerRepository.save(Broker(name = "PW-BROKER-ZERO-PRICE", owner = user))
        val p1 = portfolio("PWZP-P1")
        val asset = asset("PWZPA1")
        buy(p1, broker, asset, "10")

        assertThatThrownBy {
            trnBrokerService.proposeWeighted(
                broker.id,
                BrokerProposalRequest(
                    assetId = asset.id,
                    weight = BigDecimal("0.5"),
                    price = BigDecimal.ZERO
                )
            )
        }.isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("Price must be greater than 0")
    }

    @Test
    fun `should default settlement currency to the trade currency via the mapper`() {
        // Locks in the mapper-default path (#1041 effectiveCashCurrency) now that
        // proposeWeighted no longer sets cashCurrency on the TrnInput itself —
        // TrnInputMapper is the single authority for defaulting settlement currency.
        val user = login("propose-mapper-default@test.com")
        val broker = brokerRepository.save(Broker(name = "PW-BROKER-MAPPER-DEFAULT", owner = user))
        val p1 = portfolio("PWMD-P1")
        val asset = asset("PWMDA1")
        buy(p1, broker, asset, "100")
        val settlementAccount = asset("PWMD-USD-CASH")
        brokerSettlementAccountRepository.save(
            BrokerSettlementAccount(broker = broker, currencyCode = USD.code, account = settlementAccount)
        )

        val response =
            trnBrokerService.proposeWeighted(
                broker.id,
                BrokerProposalRequest(
                    assetId = asset.id,
                    weight = BigDecimal("1"),
                    price = BigDecimal("10.00")
                )
            )

        val trns = response.data.trns
        assertThat(trns).hasSize(1)
        assertThat(trns.first().cashCurrencyCode).isEqualTo(USD.code)
        assertThat(trns.first().cashAssetId).isEqualTo(settlementAccount.id)
    }

    @Test
    fun `should settle proposed SELL to the broker's configured settlement account`() {
        val user = login("propose-settlement@test.com")
        val broker = brokerRepository.save(Broker(name = "PW-BROKER-SETTLE", owner = user))
        val p1 = portfolio("PWSET-P1")
        val asset = asset("PWSETA1")
        buy(p1, broker, asset, "100")
        // Broker settles USD trades to this designated account.
        val settlementAccount = asset("PWSET-USD-CASH")
        brokerSettlementAccountRepository.save(
            BrokerSettlementAccount(broker = broker, currencyCode = USD.code, account = settlementAccount)
        )

        val response =
            trnBrokerService.proposeWeighted(
                broker.id,
                BrokerProposalRequest(
                    assetId = asset.id,
                    weight = BigDecimal("1"),
                    price = BigDecimal("10.00")
                )
            )

        val trns = response.data.trns
        assertThat(trns).hasSize(1)
        assertThat(trns.first().cashAssetId).isEqualTo(settlementAccount.id)
    }

    @Test
    fun `should sell full holding when weight is 1`() {
        val user = login("propose-full@test.com")
        val broker = brokerRepository.save(Broker(name = "PW-BROKER2", owner = user))
        val p1 = portfolio("PWF-P1")
        val asset = asset("PWA2")
        buy(p1, broker, asset, "75")

        val response =
            trnBrokerService.proposeWeighted(
                broker.id,
                BrokerProposalRequest(
                    assetId = asset.id,
                    weight = BigDecimal("1"),
                    price = BigDecimal("5.00")
                )
            )

        val trns = response.data.trns
        assertThat(trns).hasSize(1)
        assertThat(trns.first().quantity).isEqualByComparingTo("75")
    }

    @Test
    fun `should skip portfolios with zero or negative holding at the broker`() {
        val user = login("propose-skip-zero@test.com")
        val broker = brokerRepository.save(Broker(name = "PW-BROKER3", owner = user))
        val p1 = portfolio("PWZ-P1")
        val p2 = portfolio("PWZ-P2")
        val asset = asset("PWA3")
        // p1 bought and sold everything -> net zero at this broker
        buy(p1, broker, asset, "50")
        sell(p1, broker, asset, "50")
        // p2 still holds
        buy(p2, broker, asset, "100")

        val response =
            trnBrokerService.proposeWeighted(
                broker.id,
                BrokerProposalRequest(
                    assetId = asset.id,
                    weight = BigDecimal("0.5"),
                    price = BigDecimal("2.00")
                )
            )

        val trns = response.data.trns
        assertThat(trns).hasSize(1)
        assertThat(trns.first().portfolioId).isEqualTo(p2.id)
        assertThat(trns.first().quantity).isEqualByComparingTo("50")
    }

    @Test
    fun `should apply split adjustment to weighted quantity`() {
        val user = login("propose-split@test.com")
        val broker = brokerRepository.save(Broker(name = "PW-BROKER4", owner = user))
        val p1 = portfolio("PWS-P1")
        val asset = asset("PWA4")
        buy(p1, broker, asset, "10", tradeDate = LocalDate.of(2026, 1, 1))
        // 2:1 SPLIT — corporate action, carries NO broker
        trnRepository.save(
            Trn(
                trnType = TrnType.SPLIT,
                asset = asset,
                quantity = BigDecimal("2"),
                portfolio = p1,
                broker = null,
                tradeDate = LocalDate.of(2026, 2, 1)
            )
        )

        val response =
            trnBrokerService.proposeWeighted(
                broker.id,
                BrokerProposalRequest(
                    assetId = asset.id,
                    weight = BigDecimal("0.5"),
                    price = BigDecimal("1.00")
                )
            )

        val trns = response.data.trns
        assertThat(trns).hasSize(1)
        // 10 -> *2 (split) = 20 holding; weight 0.5 -> 10
        assertThat(trns.first().quantity).isEqualByComparingTo("10")
    }

    @Test
    fun `should round weighted quantity to whole shares by default`() {
        val user = login("propose-whole-shares@test.com")
        val broker = brokerRepository.save(Broker(name = "PW-BROKER-LOT1", owner = user))
        val p1 = portfolio("PWL1-P1")
        val p2 = portfolio("PWL1-P2")
        val asset = asset("PWLA1")
        buy(p1, broker, asset, "69")
        buy(p2, broker, asset, "18")

        val response =
            trnBrokerService.proposeWeighted(
                broker.id,
                BrokerProposalRequest(
                    assetId = asset.id,
                    weight = BigDecimal("0.5"),
                    price = BigDecimal("687.08")
                )
            )

        val byPortfolio = response.data.trns.associateBy { it.portfolioId }
        assertThat(byPortfolio).hasSize(2)
        // 34.5 raw -> whole share HALF_UP -> 35; 9 stays 9
        assertThat(byPortfolio[p1.id]!!.quantity).isEqualByComparingTo("35")
        assertThat(byPortfolio[p2.id]!!.quantity).isEqualByComparingTo("9")
        assertThat(
            trnBrokerService
                .getBrokerHoldings(broker.id)
                .holdings
                .first()
                .boardLot
        ).isEqualTo(1)
    }

    @Test
    fun `should round to board lot multiples when accounting type defines a lot`() {
        val user = login("propose-board-lot@test.com")
        val broker = brokerRepository.save(Broker(name = "PW-BROKER-LOT100", owner = user))
        val p1 = portfolio("PWL2-P1")
        val p2 = portfolio("PWL2-P2")
        val asset = assetWithLot("PWLA2", 100)
        buy(p1, broker, asset, "1000")
        buy(p2, broker, asset, "130")

        val response =
            trnBrokerService.proposeWeighted(
                broker.id,
                BrokerProposalRequest(
                    assetId = asset.id,
                    weight = BigDecimal("0.5"),
                    price = BigDecimal("2.00")
                )
            )

        val byPortfolio = response.data.trns.associateBy { it.portfolioId }
        assertThat(byPortfolio).hasSize(2)
        assertThat(byPortfolio[p1.id]!!.quantity).isEqualByComparingTo("500")
        // 65 raw -> nearest 100-lot -> 100 (still within the 130 held)
        assertThat(byPortfolio[p2.id]!!.quantity).isEqualByComparingTo("100")
        assertThat(
            trnBrokerService
                .getBrokerHoldings(broker.id)
                .holdings
                .first()
                .boardLot
        ).isEqualTo(100)
    }

    @Test
    fun `should skip portfolio when rounded quantity is below one board lot`() {
        val user = login("propose-below-lot@test.com")
        val broker = brokerRepository.save(Broker(name = "PW-BROKER-LOT-SKIP", owner = user))
        val p1 = portfolio("PWL3-P1")
        val asset = assetWithLot("PWLA3", 100)
        buy(p1, broker, asset, "40")

        val response =
            trnBrokerService.proposeWeighted(
                broker.id,
                BrokerProposalRequest(
                    assetId = asset.id,
                    weight = BigDecimal("0.5"),
                    price = BigDecimal("2.00")
                )
            )

        // 20 raw -> rounds to zero lots -> nothing proposed
        assertThat(response.data.trns).isEmpty()
    }

    @Test
    fun `should not round above held quantity`() {
        val user = login("propose-cap-held@test.com")
        val broker = brokerRepository.save(Broker(name = "PW-BROKER-LOT-CAP", owner = user))
        val p1 = portfolio("PWL4-P1")
        val asset = assetWithLot("PWLA4", 10)
        buy(p1, broker, asset, "9")

        val response =
            trnBrokerService.proposeWeighted(
                broker.id,
                BrokerProposalRequest(
                    assetId = asset.id,
                    weight = BigDecimal("1"),
                    price = BigDecimal("2.00")
                )
            )

        // 9 raw -> nearest lot 10 would oversell -> capped to floor (0 lots) -> skipped
        assertThat(response.data.trns).isEmpty()
    }

    @Test
    fun `should keep fractional quantity when accounting type board lot is zero`() {
        val user = login("propose-fractional@test.com")
        val broker = brokerRepository.save(Broker(name = "PW-BROKER-LOT0", owner = user))
        val p1 = portfolio("PWL5-P1")
        val asset = assetWithLot("PWLA5", 0)
        buy(p1, broker, asset, "69")

        val response =
            trnBrokerService.proposeWeighted(
                broker.id,
                BrokerProposalRequest(
                    assetId = asset.id,
                    weight = BigDecimal("0.5"),
                    price = BigDecimal("687.08")
                )
            )

        assertThat(response.data.trns).hasSize(1)
        assertThat(
            response.data.trns
                .first()
                .quantity
        ).isEqualByComparingTo("34.5")
    }

    @Test
    fun `should reject when a proposed sell already exists for the asset at the broker`() {
        val user = login("propose-duplicate@test.com")
        val broker = brokerRepository.save(Broker(name = "PW-BROKER-DUP", owner = user))
        val p1 = portfolio("PWD-P1")
        val asset = asset("PWDA1")
        buy(p1, broker, asset, "100")

        val first =
            trnBrokerService.proposeWeighted(
                broker.id,
                BrokerProposalRequest(
                    assetId = asset.id,
                    weight = BigDecimal("0.5"),
                    price = BigDecimal("2.00")
                )
            )
        assertThat(first.data.trns).hasSize(1)

        assertThatThrownBy {
            trnBrokerService.proposeWeighted(
                broker.id,
                BrokerProposalRequest(
                    assetId = asset.id,
                    weight = BigDecimal("0.25"),
                    price = BigDecimal("2.00")
                )
            )
        }.isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("proposed sell already exists")
    }

    @Test
    fun `should allow proposing when prior proposal was settled`() {
        val user = login("propose-after-settle@test.com")
        val broker = brokerRepository.save(Broker(name = "PW-BROKER-SETTLED", owner = user))
        val p1 = portfolio("PWAS-P1")
        val asset = asset("PWSA1")
        buy(p1, broker, asset, "100")
        // A SETTLED sell at this broker must not block a new proposal
        sell(p1, broker, asset, "20")

        val response =
            trnBrokerService.proposeWeighted(
                broker.id,
                BrokerProposalRequest(
                    assetId = asset.id,
                    weight = BigDecimal("0.5"),
                    price = BigDecimal("2.00")
                )
            )

        assertThat(response.data.trns).hasSize(1)
        // holding after settled sell: 80; weight 0.5 -> 40
        assertThat(
            response.data.trns
                .first()
                .quantity
        ).isEqualByComparingTo("40")
    }

    @Test
    fun `should reject weight above 1`() {
        val user = login("propose-weight-high@test.com")
        val broker = brokerRepository.save(Broker(name = "PW-BROKER5", owner = user))
        val p1 = portfolio("PWH-P1")
        val asset = asset("PWA5")
        buy(p1, broker, asset, "10")

        assertThatThrownBy {
            trnBrokerService.proposeWeighted(
                broker.id,
                BrokerProposalRequest(
                    assetId = asset.id,
                    weight = BigDecimal("1.5"),
                    price = BigDecimal("1.00")
                )
            )
        }.isInstanceOf(BusinessException::class.java)
    }

    @Test
    fun `should reject non-SELL type`() {
        val user = login("propose-non-sell@test.com")
        val broker = brokerRepository.save(Broker(name = "PW-BROKER6", owner = user))
        val p1 = portfolio("PWN-P1")
        val asset = asset("PWA6")
        buy(p1, broker, asset, "10")

        assertThatThrownBy {
            trnBrokerService.proposeWeighted(
                broker.id,
                BrokerProposalRequest(
                    assetId = asset.id,
                    trnType = TrnType.BUY,
                    weight = BigDecimal("0.5"),
                    price = BigDecimal("1.00")
                )
            )
        }.isInstanceOf(BusinessException::class.java)
    }

    @Test
    fun `should reject unknown asset for broker`() {
        val user = login("propose-unknown-asset@test.com")
        val broker = brokerRepository.save(Broker(name = "PW-BROKER7", owner = user))
        val p1 = portfolio("PWU-P1")
        val heldAsset = asset("PWA7")
        buy(p1, broker, heldAsset, "10")
        val unheldAsset = asset("PWA7B")

        assertThatThrownBy {
            trnBrokerService.proposeWeighted(
                broker.id,
                BrokerProposalRequest(
                    assetId = unheldAsset.id,
                    weight = BigDecimal("0.5"),
                    price = BigDecimal("1.00")
                )
            )
        }.isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `should reject NO_BROKER sentinel`() {
        login("propose-no-broker@test.com")

        assertThatThrownBy {
            trnBrokerService.proposeWeighted(
                TrnBrokerService.NO_BROKER,
                BrokerProposalRequest(
                    assetId = "any-asset-id",
                    weight = BigDecimal("0.5"),
                    price = BigDecimal("1.00")
                )
            )
        }.isInstanceOf(BusinessException::class.java)
    }
}