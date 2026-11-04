package com.beancounter.marketdata.trn.settlement

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.contracts.TrnStatusUpdateRequest
import com.beancounter.common.contracts.TrnStatusUpdateResponse
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.trn.ProposedScope
import com.beancounter.marketdata.trn.SettleTransactionsRequest
import com.beancounter.marketdata.trn.TrnFinder
import com.beancounter.marketdata.trn.UserTrnFinder
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Trn status: proposed and settled trns, settling and unsettling them, and the cash they move.
 */
@RestController
@RequestMapping("/trns")
@CrossOrigin
@PreAuthorize(
    AuthConstants.USER_OR_SYSTEM
)
@Tag(
    name = "Transaction Management",
    description = "Operations for managing financial transactions including trades, dividends, and corporate actions"
)
class TrnSettlementController(
    private val trnFinder: TrnFinder,
    private val userTrnFinder: UserTrnFinder,
    private val trnSettlementService: TrnSettlementService,
    private val dateUtils: DateUtils
) {
    @PatchMapping(
        value = ["/{trnId}/status"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Update transaction status",
        description = """
            Change a transaction's status (e.g. SETTLED → PROPOSED to Unsettle).
            For Unsettle, the response includes the ids of any auto-settled
            sibling transactions (the WITHDRAWAL + DEPOSIT cash legs) so the
            UI can prompt the user to delete them. The server does NOT
            cascade.
        """
    )
    fun updateStatus(
        @Parameter(
            description = "Transaction identifier",
            example = "trn-123"
        ) @PathVariable("trnId") trnId: String,
        @RequestBody request: TrnStatusUpdateRequest
    ): TrnStatusUpdateResponse {
        require(request.status == TrnStatus.PROPOSED) {
            "Only PROPOSED is supported via this endpoint (Unsettle). Use POST /trns/settle for SETTLED."
        }
        return trnSettlementService.unsettle(trnId)
    }

    @GetMapping(
        value = ["/portfolio/{portfolioId}/status/{status}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get transactions for portfolio by status",
        description = """
            Retrieves all transactions for a portfolio with a specific status.
            Valid statuses: PROPOSED, SETTLED

            Use this to:
            * View pending/proposed transactions that need review
            * Filter transactions by their processing status
            * Find unsettled transactions for a portfolio
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Transactions retrieved successfully"
            ), ApiResponse(
                responseCode = "404",
                description = "Portfolio not found"
            )
        ]
    )
    fun findByStatus(
        @Parameter(
            description = "Portfolio identifier",
            example = "portfolio-123"
        ) @PathVariable("portfolioId") portfolioId: String,
        @Parameter(
            description = "Transaction status to filter by",
            example = "PROPOSED"
        ) @PathVariable("status") status: String
    ): TrnResponse {
        val trnStatus =
            com.beancounter.common.model.TrnStatus
                .valueOf(status.uppercase())
        return TrnResponse(trnFinder.findByStatus(portfolioId, trnStatus))
    }

    @GetMapping(
        value = ["/proposed"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get all proposed transactions for current user",
        description = """
            Retrieves all PROPOSED transactions across all portfolios owned by the current user.
            This is useful for showing a unified view of all pending transactions that need review.

            Use this to:
            * Display all pending transactions in a notification center
            * Show users what transactions need to be settled
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Proposed transactions retrieved successfully"
            )
        ]
    )
    fun findProposed(
        @RequestParam(value = "scope", required = false, defaultValue = "ALL") scope: ProposedScope,
        @Parameter(description = "Show proposed transactions due on or before this date (default today)")
        @RequestParam(value = "asAt", required = false) asAt: String?
    ): TrnResponse =
        TrnResponse(
            userTrnFinder.findProposedForUser(scope, dateUtils.getFormattedDate(asAt ?: dateUtils.today()))
        )

    @GetMapping(
        value = ["/proposed/count"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get count of proposed transactions for current user",
        description = """
            Returns the count of PROPOSED transactions across all portfolios owned by the current user.
            This is useful for displaying a badge or notification indicator.

            Use this to:
            * Display a notification badge count
            * Quickly check if there are pending transactions
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Count retrieved successfully"
            )
        ]
    )
    fun countProposed(
        @RequestParam(value = "scope", required = false, defaultValue = "ALL") scope: ProposedScope,
        @Parameter(description = "Count proposed transactions due on or before this date (default today)")
        @RequestParam(value = "asAt", required = false) asAt: String?
    ): Map<String, Long> =
        mapOf(
            "count" to userTrnFinder.countProposedForUser(scope, dateUtils.getFormattedDate(asAt ?: dateUtils.today()))
        )

    @GetMapping(
        value = ["/settled"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get all settled transactions for current user within a date range",
        description = """
            Retrieves all SETTLED transactions across all portfolios owned by the current user
            whose tradeDate falls within the inclusive from..to window.

            Use this to:
            * Review what was executed over a trading window
            * Pair with the proposed list on the Transactions review page

            `from`/`to` default to today. The legacy single-date `tradeDate` param is still
            honoured (treated as from = to = tradeDate) when from/to are absent.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Settled transactions retrieved successfully"
            )
        ]
    )
    fun findSettled(
        @Parameter(description = "Window start (YYYY-MM-DD); defaults to today")
        @RequestParam(value = "from", required = false) from: String?,
        @Parameter(description = "Window end (YYYY-MM-DD); defaults to today")
        @RequestParam(value = "to", required = false) to: String?,
        @Parameter(description = "Legacy single trade date (YYYY-MM-DD); used when from/to absent")
        @RequestParam(value = "tradeDate", required = false) tradeDate: String?
    ): TrnResponse {
        // Range mode wins as soon as either bound is supplied; the legacy single
        // `tradeDate` is honoured only when neither from nor to is present, so a
        // mixed `from` + `tradeDate` can't silently produce an unintended window.
        val rangeMode = from != null || to != null
        val legacy = tradeDate ?: dateUtils.today()
        val fromDate = dateUtils.getFormattedDate(if (rangeMode) from ?: dateUtils.today() else legacy)
        val toDate = dateUtils.getFormattedDate(if (rangeMode) to ?: dateUtils.today() else legacy)
        return TrnResponse(userTrnFinder.findSettledForUser(fromDate, toDate))
    }

    @PostMapping(
        value = ["/portfolio/{portfolioId}/settle"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Settle proposed transactions",
        description = """
            Updates the status of PROPOSED transactions to SETTLED.
            Only transactions with PROPOSED status will be affected.

            Use this to:
            * Finalize proposed transactions after review
            * Convert pending rebalance transactions to actual trades
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Transactions settled successfully"
            ), ApiResponse(
                responseCode = "404",
                description = "Portfolio not found"
            )
        ]
    )
    fun settleTransactions(
        @Parameter(
            description = "Portfolio identifier",
            example = "portfolio-123"
        ) @PathVariable("portfolioId") portfolioId: String,
        @Parameter(
            description = "Request body containing transaction IDs to settle"
        ) @RequestBody request: SettleTransactionsRequest
    ): TrnResponse = TrnResponse(trnSettlementService.settleTransactions(portfolioId, request.trnIds))

    @PostMapping(
        value = ["/portfolio/{portfolioId}/unsettle"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Unsettle settled transactions",
        description = """
            Reverts SETTLED transactions to PROPOSED, cascade-deleting each one's
            auto-emitted cash legs (same per-trn core as single Unsettle). Non-SETTLED
            or missing ids are skipped, never aborting the batch.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Transactions unsettled"),
            ApiResponse(responseCode = "404", description = "Portfolio not found")
        ]
    )
    fun unsettleTransactions(
        @Parameter(
            description = "Portfolio identifier",
            example = "portfolio-123"
        ) @PathVariable("portfolioId") portfolioId: String,
        @Parameter(
            description = "Request body containing transaction IDs to unsettle"
        ) @RequestBody request: SettleTransactionsRequest
    ): TrnResponse = TrnResponse(trnSettlementService.unsettleTransactions(portfolioId, request.trnIds))

    @GetMapping(
        value = ["/{portfolioId}/cash-ladder/{cashAssetId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get cash ladder for a specific cash asset",
        description = """
            Retrieves all transactions that impacted a specific cash position (settlement account).
            This is useful for account reconciliation, showing all buys, sells, deposits, withdrawals
            and other transactions that affected the cash balance.

            Use this to:
            * View all transactions settled to a specific cash account
            * Reconcile cash balances with external statements
            * Track cash flow for a specific settlement account
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Cash ladder retrieved successfully"
            ), ApiResponse(
                responseCode = "404",
                description = "Portfolio or cash asset not found"
            )
        ]
    )
    fun getCashLadder(
        @Parameter(
            description = "Portfolio identifier",
            example = "portfolio-123"
        ) @PathVariable("portfolioId") portfolioId: String,
        @Parameter(
            description = "Cash asset identifier",
            example = "cash-usd-123"
        ) @PathVariable("cashAssetId") cashAssetId: String
    ): TrnResponse = TrnResponse(trnFinder.getCashLadder(portfolioId, cashAssetId))

    @GetMapping(
        value = ["/portfolio/{portfolioId}/proposed-cash"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(summary = "Proposed cash legs (DEPOSIT/WITHDRAWAL) for a portfolio as at a date")
    fun findProposedCash(
        @PathVariable portfolioId: String,
        @RequestParam(required = false) asAt: String? = null
    ): TrnResponse =
        TrnResponse(
            trnFinder.findProposedCash(portfolioId, dateUtils.getFormattedDate(asAt ?: dateUtils.today()))
        )
}