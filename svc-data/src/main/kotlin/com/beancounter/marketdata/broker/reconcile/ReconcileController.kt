package com.beancounter.marketdata.broker.reconcile

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.TrnResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Previews what recording a brokerage statement would do, and records the rows the user confirms. */
@RestController
@RequestMapping("/brokers/reconcile")
@PreAuthorize(AuthConstants.USER_OR_SYSTEM)
class ReconcileController(
    private val reconcileService: ReconcileService,
    private val reconcileCommitService: ReconcileCommitService,
    private val heldSinceService: HeldSinceService
) {
    @PostMapping
    fun preview(
        @RequestBody shown: ShownHoldings
    ): ReconcilePreviewResponse = ReconcilePreviewResponse(reconcileService.preview(shown))

    /** When each holding was probably opened, from its cost and the asset's price history. */
    @PostMapping("/held-since")
    fun heldSince(
        @RequestBody request: HeldSinceRequest
    ): HeldSinceResponse = HeldSinceResponse(heldSinceService.suggest(request))

    @PostMapping("/commit")
    fun commit(
        @RequestBody confirmed: ConfirmedHoldings
    ): TrnResponse = reconcileCommitService.commit(confirmed).let { TrnResponse(it.trns, it.warnings) }
}