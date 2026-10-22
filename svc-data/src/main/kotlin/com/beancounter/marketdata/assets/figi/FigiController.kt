package com.beancounter.marketdata.assets.figi

import com.beancounter.auth.model.AuthConstants
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Admin operations against OpenFIGI.
 */
@RestController
@RequestMapping("/assets/figi")
@CrossOrigin
@PreAuthorize("hasAuthority('${AuthConstants.SCOPE_ADMIN}')")
@ConditionalOnProperty(
    value = ["beancounter.marketdata.provider.figi.enabled"],
    matchIfMissing = true
)
@Tag(name = "Assets", description = "Asset management operations")
class FigiController(
    private val figiBackfillService: FigiBackfillService
) {
    @PostMapping(
        value = ["/backfill"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Backfill composite FIGIs for existing assets",
        description =
            "Resolves up to `limit` (max ${FigiBackfillService.MAX_LIMIT}) active, unowned assets " +
                "that have no composite FIGI. Pass the previous lastId as `after` to continue; done when attempted is 0."
    )
    fun backfill(
        @Parameter(description = "Assets to attempt, clamped to 1..${FigiBackfillService.MAX_LIMIT}", example = "50")
        @RequestParam(required = false, defaultValue = "50") limit: Int = 50,
        @Parameter(description = "Asset id cursor: the previous run's lastId")
        @RequestParam(required = false) after: String? = null
    ): FigiBackfillResult = figiBackfillService.backfill(limit, after)
}