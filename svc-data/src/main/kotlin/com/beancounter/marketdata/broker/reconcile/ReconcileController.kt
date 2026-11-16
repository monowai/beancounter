package com.beancounter.marketdata.broker.reconcile

import com.beancounter.auth.model.AuthConstants
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Previews what recording a brokerage statement would do. Records nothing. */
@RestController
@RequestMapping("/brokers/reconcile")
@PreAuthorize(AuthConstants.USER_OR_SYSTEM)
class ReconcileController(
    private val reconcileService: ReconcileService
) {
    @PostMapping
    fun preview(
        @RequestBody shown: ShownHoldings
    ): ReconcilePreviewResponse = ReconcilePreviewResponse(reconcileService.preview(shown))
}