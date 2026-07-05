package com.beancounter.position.composite

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.model.Position
import com.beancounter.common.utils.DateUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Synthesises a Position for composite assets (CPF, ILP, ...) whose balance
 * lives in a sub-account list rather than a transaction stream. Returns
 * data = null for non-composite assets so callers can fall back to the
 * standard whereHeld/positions flow without a 404.
 */
@RestController
@RequestMapping("/positions/composite")
@CrossOrigin
@PreAuthorize(
    "hasAnyAuthority('" + AuthConstants.SCOPE_USER + "', '" + AuthConstants.SCOPE_SYSTEM + "')"
)
@Tag(
    name = "Composite Position",
    description = "Synthesised positions for composite assets (CPF, ILP, ...)"
)
class CompositeController(
    private val service: CompositeValuationService
) {
    @GetMapping("/{assetId}")
    @Operation(summary = "Get synthesised position for a composite asset")
    fun byId(
        @PathVariable assetId: String,
        @RequestParam(name = "asAt", required = false, defaultValue = DateUtils.TODAY)
        asAt: String
    ): CompositePositionResponse {
        val date = dateUtils.getDate(asAt)
        return CompositePositionResponse(service.valueFor(assetId, date))
    }

    companion object {
        private val dateUtils = DateUtils()
    }
}

data class CompositePositionResponse(
    val data: Position?
)