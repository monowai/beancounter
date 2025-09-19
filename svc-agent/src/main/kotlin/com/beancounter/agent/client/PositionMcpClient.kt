package com.beancounter.agent.client

import com.beancounter.agent.config.FeignAuthInterceptor
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Portfolio
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam

/**
 * Feign client for Position MCP service
 */
@FeignClient(
    name = "position-mcp",
    url = "\${position.url}",
    path = "/api/mcp",
    configuration = [FeignAuthInterceptor::class]
)
interface PositionMcpClient {
    @GetMapping("/ping")
    fun ping(): Map<String, String>

    @PostMapping("/portfolio/positions")
    fun getPortfolioPositions(
        @RequestBody portfolio: Portfolio,
        @RequestParam valuationDate: String
    ): PositionResponse

    @PostMapping("/query")
    fun queryPositions(
        @RequestBody query: TrustedTrnQuery
    ): PositionResponse

    @PostMapping("/portfolio/build")
    fun buildPositions(
        @RequestBody portfolio: Portfolio,
        @RequestParam valuationDate: String
    ): PositionResponse

    @PostMapping("/value")
    fun valuePositions(
        @RequestBody positionResponse: PositionResponse
    ): PositionResponse

    @PostMapping("/portfolio/metrics")
    fun getPortfolioMetrics(
        @RequestBody portfolio: Portfolio,
        @RequestParam valuationDate: String
    ): Map<String, Any>

    @PostMapping("/portfolio/breakdown")
    fun getPositionBreakdown(
        @RequestBody portfolio: Portfolio,
        @RequestParam valuationDate: String
    ): Map<String, Any>
}