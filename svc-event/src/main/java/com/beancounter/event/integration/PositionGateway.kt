package com.beancounter.event.integration

import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.input.TrustedTrnQuery
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod

@FeignClient(name = "bcPosition", url = "\${position.url:http://localhost:9500/api}")
interface PositionGateway {
    @RequestMapping(method = [RequestMethod.POST], value = ["/query"], produces = [MediaType.APPLICATION_JSON_VALUE], consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun query(
            @RequestHeader("Authorization") bearerToken: String?, trnQuery: TrustedTrnQuery?): PositionResponse?

    @RequestMapping(method = [RequestMethod.GET], value = ["/id/{id}/{asAt}?value=false"], produces = [MediaType.APPLICATION_JSON_VALUE], consumes = [MediaType.APPLICATION_JSON_VALUE])
    operator fun get(
            @RequestHeader("Authorization") bearerToken: String?,
            @PathVariable("id") code: String?, @PathVariable("asAt") asAt: String?): PositionResponse?
}