package com.beancounter.marketdata.trn

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.utils.DateUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Controller for broker-related transaction operations.
 * Provides endpoints for viewing transactions and holdings by broker.
 */
@RestController
@RequestMapping("/trns")
@CrossOrigin
@PreAuthorize(
    "hasAnyAuthority('" + AuthConstants.SCOPE_USER + "', '" + AuthConstants.SCOPE_SYSTEM + "')"
)
@Tag(
    name = "Transaction Broker",
    description = "Broker-related transaction operations including holdings reconciliation"
)
class TrnBrokerController(
    var trnBrokerService: TrnBrokerService,
    var dateUtils: DateUtils
) {
    @GetMapping(
        value = ["/broker/{brokerId}/holdings"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get holdings by broker for reconciliation",
        description = """
            Calculates holdings (quantities) for all assets transacted through a specific broker.
            This aggregates settled transactions to show net positions per asset.

            Use this to:
            * Reconcile holdings with broker statements
            * Verify quantities match broker records
            * Identify discrepancies between system and broker
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Broker holdings retrieved successfully"
            ),
            ApiResponse(
                responseCode = "404",
                description = "Broker not found"
            )
        ]
    )
    fun getBrokerHoldings(
        @Parameter(
            description = "Broker identifier",
            example = "broker-123"
        ) @PathVariable("brokerId") brokerId: String
    ): BrokerHoldingsResponse = trnBrokerService.getBrokerHoldings(brokerId)

    @GetMapping(
        value = ["/broker/{brokerId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get transactions by broker for position building",
        description = """
            Retrieves all transactions for a specific broker as of a given date.
            Includes all transaction types (BUY, SELL, SPLIT, DIVI, etc.) needed
            for accurate position calculation with split adjustments.

            Used by svc-position to build holdings with correct quantities.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Broker transactions retrieved successfully"
            ),
            ApiResponse(
                responseCode = "404",
                description = "Broker not found"
            )
        ]
    )
    fun getBrokerTransactions(
        @Parameter(
            description = "Broker identifier",
            example = "broker-123"
        ) @PathVariable("brokerId") brokerId: String,
        @Parameter(
            description = "Date to retrieve transactions for (YYYY-MM-DD format)",
            example = "2024-01-15"
        ) @RequestParam(required = false) asAt: String? = null
    ): TrnResponse =
        TrnResponse(
            trnBrokerService.findForBroker(
                brokerId,
                dateUtils.getFormattedDate(asAt ?: dateUtils.today())
            )
        )

    @PostMapping(
        value = ["/broker/{brokerId}/propose"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Propose weighted SELL transactions from broker holdings",
        description = """
            Creates PROPOSED SELL transactions for every portfolio holding the given asset
            at the broker, sized as `weight` * that portfolio's split-adjusted broker holding.

            Use this to:
            * Kick off a broker-wide partial (or full) liquidation from the reconciliation view
            * Stage proposals for review/settle without directly mutating live positions
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Proposed transactions created successfully"
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid proposal request"
            ),
            ApiResponse(
                responseCode = "404",
                description = "Broker or asset not found"
            )
        ]
    )
    fun proposeWeighted(
        @Parameter(
            description = "Broker identifier",
            example = "broker-123"
        ) @PathVariable("brokerId") brokerId: String,
        @Parameter(
            description = "Weighted SELL proposal request"
        ) @RequestBody request: BrokerProposalRequest
    ): TrnResponse = trnBrokerService.proposeWeighted(brokerId, request)
}