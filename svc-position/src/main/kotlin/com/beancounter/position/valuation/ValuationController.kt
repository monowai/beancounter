package com.beancounter.position.valuation

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.PositionResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Still thinking on this.
 *
 * @author mikeh
 * @since 2019-02-01
 */
@RestController
@RequestMapping("/value")
@PreAuthorize("hasAnyAuthority('" + AuthConstants.SCOPE_USER + "', '" + AuthConstants.SCOPE_SYSTEM + "')")
class ValuationController @Autowired internal constructor(private val valuationService: Valuation) {
    @PostMapping
    fun value(@RequestBody positions: PositionResponse): PositionResponse =
        valuationService.value(positions.data)
}
