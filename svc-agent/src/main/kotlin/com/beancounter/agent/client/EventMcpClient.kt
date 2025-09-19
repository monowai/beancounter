package com.beancounter.agent.client

import com.beancounter.agent.config.FeignAuthInterceptor
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

/**
 * Feign client for Event MCP service
 */
@FeignClient(
    name = "event-mcp",
    url = "\${event.url}",
    path = "/api/mcp",
    configuration = [FeignAuthInterceptor::class]
)
interface EventMcpClient {
    @GetMapping("/ping")
    fun ping(): Map<String, String>

    @GetMapping("/asset/{assetId}/events")
    fun getAssetEvents(
        @PathVariable assetId: String
    ): Map<String, Any>

    @PostMapping("/portfolio/{portfolioId}/load-events")
    fun loadEventsForPortfolio(
        @PathVariable portfolioId: String,
        @RequestParam fromDate: String
    ): Map<String, Any>

    @PostMapping("/portfolio/{portfolioId}/backfill")
    fun backfillEvents(
        @PathVariable portfolioId: String,
        @RequestParam fromDate: String,
        @RequestParam(required = false) toDate: String?
    ): Map<String, Any>
}