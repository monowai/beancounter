package com.beancounter.shell.commands

import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.contracts.PortfoliosRequest
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.Portfolio
import com.beancounter.common.utils.BcJson.Companion.writer
import com.fasterxml.jackson.core.JsonProcessingException
import org.slf4j.LoggerFactory
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.ShellOption

/**
 * Portfolio Access commands
 */
@ShellComponent
class PortfolioCommands(
    private val portfolioService: PortfolioServiceClient
) {
    private val log = LoggerFactory.getLogger(PortfolioCommands::class.java)

    @ShellMethod("Find portfolio by code")
    @Throws(JsonProcessingException::class)
    fun portfolioCode(
        @ShellOption(help = "Code - case insensitive") portfolioCode: String
    ): String {
        val portfolio = portfolioService.getPortfolioByCode(portfolioCode)
        return writer.writeValueAsString(portfolio)
    }

    @ShellMethod("My Portfolios")
    @Throws(JsonProcessingException::class)
    fun portfolios(): String {
        val (data) = portfolioService.portfolios
        return if (data.isEmpty()) {
            "No portfolios"
        } else {
            writer.writeValueAsString(data)
        }
    }

    @ShellMethod("Find by id")
    @Throws(JsonProcessingException::class)
    fun portfolio(
        @ShellOption(help = "Primary key - case sensitive") portfolioId: String
    ): String {
        val portfolio = portfolioService.getPortfolioById(portfolioId)
        return writer.writeValueAsString(portfolio)
    }

    @ShellMethod(
        key = ["add"],
        value = "Add portfolio"
    )
    @Throws(JsonProcessingException::class)
    fun add(
        @ShellOption(help = "Unique Code") code: String,
        @ShellOption(help = "Name") name: String,
        @ShellOption(help = "Reference currency") currencyCode: String,
        @ShellOption(help = "Base currency - defaults to USD") baseCurrency: String = "USD"
    ): String {
        val portfolio: Portfolio
        try {
            portfolio = portfolioService.getPortfolioByCode(code)
            return writer.writeValueAsString(portfolio)
        } catch (e: BusinessException) {
            log.info(
                "Creating portfolio {}",
                code
            )
        }
        val portfoliosRequest =
            PortfoliosRequest(
                setOf(
                    PortfolioInput(
                        code,
                        name,
                        baseCurrency,
                        currencyCode
                    )
                )
            )
        val (data) = portfolioService.add(portfoliosRequest)
        return writer.writeValueAsString(data.iterator().next())
    }
}