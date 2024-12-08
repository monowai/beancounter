package com.beancounter.event.integration

import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.input.TrustedTrnQuery
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod

/**
 * Integration calls to svc-position.
 */
@FeignClient(
    name = "bcPosition",
    url = "\${position.url:http://localhost:9500}",
)
interface PositionGateway {
    @RequestMapping(
        method = [RequestMethod.POST],
        value = ["/api/query"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE],
    )
    fun query(
        @RequestHeader("Authorization") bearerToken: String,
        trnQuery: TrustedTrnQuery,
    ): PositionResponse?

    @RequestMapping(
        method = [RequestMethod.GET],
        value = ["/api/id/{id}/{asAt}?value={value}"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    operator fun get(
        @RequestHeader("Authorization") bearerToken: String,
        @PathVariable("id") code: String,
        @PathVariable("asAt") asAt: String,
        @PathVariable("value") value: Boolean = false,
    ): PositionResponse
}
