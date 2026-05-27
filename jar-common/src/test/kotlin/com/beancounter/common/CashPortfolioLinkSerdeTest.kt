package com.beancounter.common

import com.beancounter.common.contracts.PortfolioResponse
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Round-trip tests for the cashPortfolioId fields on Portfolio and SystemUser.
 *
 * The field models the funding portfolio used by auto-settle to emit
 * compensating cash transfers when a cash-impacting trade lands in an
 * investment portfolio. null = no auto-settle for that portfolio/user.
 */
class CashPortfolioLinkSerdeTest {
    @Test
    fun `Portfolio cashPortfolioId defaults to null and survives round-trip`() {
        val portfolio = Portfolio(id = "INV", code = "INV", currency = Currency("USD"))
        assertThat(portfolio.cashPortfolioId).isNull()

        val json = objectMapper.writeValueAsString(PortfolioResponse(portfolio))
        val back =
            objectMapper
                .readValue(
                    json,
                    PortfolioResponse::class.java
                ).data
        assertThat(back.cashPortfolioId).isNull()
    }

    @Test
    fun `Portfolio cashPortfolioId carries through serialization when set`() {
        val portfolio =
            Portfolio(
                id = "SCB-USD",
                code = "SCB-USD",
                currency = Currency("USD"),
                cashPortfolioId = "SGD"
            )
        val json = objectMapper.writeValueAsString(PortfolioResponse(portfolio))
        val back =
            objectMapper
                .readValue(
                    json,
                    PortfolioResponse::class.java
                ).data
        assertThat(back.cashPortfolioId).isEqualTo("SGD")
    }

    @Test
    fun `SystemUser cashPortfolioId defaults to null and round-trips`() {
        val user = SystemUser(id = "u-1", email = "u@example.com")
        assertThat(user.cashPortfolioId).isNull()

        val json = objectMapper.writeValueAsString(user)
        val back = objectMapper.readValue(json, SystemUser::class.java)
        assertThat(back.cashPortfolioId).isNull()
    }

    @Test
    fun `PortfolioInput cashPortfolioId defaults to null and round-trips`() {
        val input = PortfolioInput(code = "INV", base = "USD", currency = "USD")
        assertThat(input.cashPortfolioId).isNull()

        val json = objectMapper.writeValueAsString(input)
        val back = objectMapper.readValue(json, PortfolioInput::class.java)
        assertThat(back.cashPortfolioId).isNull()
    }

    @Test
    fun `PortfolioInput cashPortfolioId carries through when set`() {
        val input =
            PortfolioInput(
                code = "SCB-USD",
                base = "USD",
                currency = "USD",
                cashPortfolioId = "sgd-master-id"
            )
        val json = objectMapper.writeValueAsString(input)
        val back = objectMapper.readValue(json, PortfolioInput::class.java)
        assertThat(back.cashPortfolioId).isEqualTo("sgd-master-id")
    }

    @Test
    fun `SystemUser cashPortfolioId carries through when set`() {
        val user =
            SystemUser(
                id = "u-1",
                email = "u@example.com",
                cashPortfolioId = "SGD"
            )
        val json = objectMapper.writeValueAsString(user)
        val back = objectMapper.readValue(json, SystemUser::class.java)
        assertThat(back.cashPortfolioId).isEqualTo("SGD")
    }
}