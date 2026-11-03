package com.beancounter.marketdata.broker

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.Registration
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.SettlementAccountMapping
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.BrokerInput
import com.beancounter.common.model.Broker
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Updating a broker replaces its editable fields and settlement accounts, and only
 * the owner may update it.
 */
@SpringMvcDbTest
internal class BrokerServiceTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var brokerService: BrokerService

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var systemUserService: Registration

    @Test
    fun `should change name, account number, notes and settlement accounts on update`() {
        val owner = SystemUser(id = "broker-update-owner", email = "broker-update-owner@test.com")
        mockAuthConfig.login(owner, systemUserService)
        val cash = settlementAsset(owner, "BROKER-UPDATE-CASH")
        val broker =
            brokerService.create(
                Broker(name = "Before", owner = owner, accountNumber = "A-1", notes = "old")
            )

        brokerService.update(
            broker.id,
            owner,
            BrokerInput(
                name = "After",
                accountNumber = "A-2",
                notes = "new",
                settlementAccounts = mapOf(USD.code to cash.id)
            )
        )

        val updated = brokerService.findByIdWithAccounts(broker.id, owner).get()
        assertThat(updated.name).isEqualTo("After")
        assertThat(updated.accountNumber).isEqualTo("A-2")
        assertThat(updated.notes).isEqualTo("new")
        assertThat(updated.settlementAccounts)
            .containsExactly(SettlementAccountMapping(USD.code, cash.id, cash.name))
    }

    @Test
    fun `should reject an update to another user's broker`() {
        val owner = SystemUser(id = "broker-owner", email = "broker-owner@test.com")
        mockAuthConfig.login(owner, systemUserService)
        val broker = brokerService.create(Broker(name = "Owned", owner = owner))

        val intruder = SystemUser(id = "broker-intruder", email = "broker-intruder@test.com")
        mockAuthConfig.login(intruder, systemUserService)

        assertThatThrownBy {
            brokerService.update(broker.id, intruder, BrokerInput(name = "Hijacked"))
        }.isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("Broker not found")
        assertThat(brokerService.findById(broker.id, owner).get().name).isEqualTo("Owned")
    }

    private fun settlementAsset(
        owner: SystemUser,
        code: String
    ) = assetService
        .handle(
            AssetRequest(
                mapOf(
                    code to
                        AssetInput.toRealEstate(
                            currency = USD,
                            code = code,
                            name = "Settlement Cash",
                            owner = owner.id
                        )
                )
            )
        ).data
        .getValue(code)
}