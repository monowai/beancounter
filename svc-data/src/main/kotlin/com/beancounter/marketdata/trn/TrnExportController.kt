package com.beancounter.marketdata.trn

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.utils.DateUtils
import com.opencsv.CSVWriterBuilder
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Controller for transaction export operations (CSV download).
 */
@RestController
@RequestMapping("/trns")
@CrossOrigin
@PreAuthorize(
    "hasAnyAuthority('" + AuthConstants.SCOPE_USER + "', '" + AuthConstants.SCOPE_SYSTEM + "')"
)
@Tag(
    name = "Transaction Export",
    description = "Export transactions to external formats"
)
class TrnExportController(
    var trnService: TrnService,
    var dateUtils: DateUtils,
    var trnIoDefinition: TrnIoDefinition
) {
    @GetMapping(value = ["/portfolio/{portfolioId}/export"])
    @Operation(
        summary = "Export portfolio transactions to CSV",
        description = """
            Exports all transactions for a portfolio to CSV format.
            The file will be downloaded with the portfolio ID as the filename.

            Use this to:
            * Export transaction data for external analysis
            * Create backup files of transaction history
            * Generate reports for compliance or auditing
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "CSV file generated and downloaded successfully",
                content = [
                    Content(
                        mediaType = "text/csv"
                    )
                ]
            ), ApiResponse(
                responseCode = "404",
                description = "Portfolio not found"
            )
        ]
    )
    fun export(
        response: HttpServletResponse,
        @Parameter(
            description = "Portfolio identifier to export",
            example = "portfolio-123"
        ) @PathVariable("portfolioId") portfolioId: String
    ) {
        response.contentType = MediaType.TEXT_PLAIN_VALUE
        response.setHeader(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"$portfolioId.csv\""
        )
        val trnResponse =
            trnService.findForPortfolio(
                portfolioId,
                dateUtils.date
            )

        val csvWriter = CSVWriterBuilder(response.writer).withSeparator(',').build()
        csvWriter.writeNext(
            trnIoDefinition.headers(),
            false
        )
        for (datum in trnResponse) {
            csvWriter.writeNext(
                trnIoDefinition.export(datum),
                false
            )
        }
        csvWriter.close()
    }
}