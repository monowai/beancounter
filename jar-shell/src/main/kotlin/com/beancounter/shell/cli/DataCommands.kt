package com.beancounter.shell.cli

import com.beancounter.client.services.StaticService
import com.beancounter.common.contracts.MarketResponse
import com.beancounter.common.model.Market
import com.beancounter.common.utils.BcJson
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.ShellOption

/**
 * Obtain Markets from the backend in response to a user command request.
 */
@ShellComponent
class DataCommands internal constructor(private val staticService: StaticService) {
    @ShellMethod("Supported markets")
    fun markets(
        @ShellOption(help = "Optional market code", defaultValue = "__NULL__") marketCode: String?,
    ): String {
        return if (marketCode != null) {
            val market = staticService.getMarket(
                marketCode
            )
            val markets: MutableCollection<Market> = ArrayList()
            markets.add(market)
            return BcJson().objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(MarketResponse(markets))
        } else {
            BcJson().objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(staticService.getMarkets())
        }
    }
}
