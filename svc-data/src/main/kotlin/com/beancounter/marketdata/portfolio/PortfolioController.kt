package com.beancounter.marketdata.portfolio

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.PortfolioResponse
import com.beancounter.common.contracts.PortfoliosRequest
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.utils.DateUtils
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import com.opencsv.CSVWriterBuilder
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * Rest controller for Portfolio activities.
 * Provides endpoints for managing investment portfolios including creation, updates, and queries.
 */
@RestController
@RequestMapping("/portfolios")
@CrossOrigin
@PreAuthorize(
    "hasAnyAuthority('" + AuthConstants.SCOPE_USER + "', '" + AuthConstants.SCOPE_SYSTEM + "')"
)
@Tag(
    name = "Portfolio Management",
    description = "Operations for managing investment portfolios including creation, updates, and queries"
)
class PortfolioController internal constructor(
    private val portfolioService: PortfolioService,
    private val dateUtils: DateUtils,
    private val portfolioIoDefinition: PortfolioIoDefinition
) {
    @GetMapping
    @Operation(
        summary = "Get all portfolios",
        description = """
            Retrieves all portfolios accessible to the current user.
            By default, only active portfolios are returned.

            Use this to:
            * List all available portfolios
            * Get portfolio overview for dashboard
            * Check portfolio access permissions
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Portfolios retrieved successfully",
                content = [
                    Content(
                        mediaType = "application/json",
                        examples = [
                            ExampleObject(
                                name = "Portfolio List",
                                value = """
                                {
                                  "data": [
                                    {
                                      "id": "portfolio-123",
                                      "code": "MY_PORTFOLIO",
                                      "name": "My Investment Portfolio",
                                      "currency": "USD",
                                      "active": true
                                    }
                                  ]
                                }
                                """
                            )
                        ]
                    )
                ]
            )
        ]
    )
    fun getPortfolios(
        @Parameter(
            description = "Include inactive portfolios in the response",
            example = "false"
        )
        @RequestParam(
            value = "inactive",
            defaultValue = "false"
        ) inactive: Boolean
    ): PortfoliosResponse = PortfoliosResponse(portfolioService.portfolios(inactive))

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('" + AuthConstants.SCOPE_SYSTEM + "')")
    @Operation(
        summary = "Get all portfolios in the system",
        description = """
            Retrieves all portfolios across all users. Requires SYSTEM scope.
            Used by scheduled jobs for system-wide portfolio valuation.
            By default, only active portfolios are returned.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "All portfolios retrieved successfully"
            ),
            ApiResponse(
                responseCode = "403",
                description = "Requires SYSTEM scope"
            )
        ]
    )
    fun getAllPortfolios(
        @Parameter(
            description = "Include inactive portfolios in the response",
            example = "false"
        )
        @RequestParam(
            value = "inactive",
            defaultValue = "false"
        ) inactive: Boolean
    ): PortfoliosResponse = PortfoliosResponse(portfolioService.findAll(inactive))

    @GetMapping("/{id}")
    @Operation(
        summary = "Get portfolio by ID",
        description = """
            Retrieves a specific portfolio by its unique identifier.

            Use this to:
            * Get detailed portfolio information
            * Access portfolio metadata and settings
            * View portfolio configuration
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Portfolio retrieved successfully"
            ),
            ApiResponse(
                responseCode = "404",
                description = "Portfolio not found"
            )
        ]
    )
    fun getPortfolio(
        @Parameter(
            description = "Unique portfolio identifier",
            example = "portfolio-123"
        )
        @PathVariable id: String
    ): PortfolioResponse = PortfolioResponse(portfolioService.find(id))

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete portfolio",
        description = """
            Permanently deletes a portfolio and all its associated data.
            This operation cannot be undone.

            Use this to:
            * Remove portfolios that are no longer needed
            * Clean up test or temporary portfolios
            * Delete portfolios for privacy or compliance
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Portfolio deleted successfully",
                content = [
                    Content(
                        mediaType = "text/plain",
                        examples = [
                            ExampleObject(
                                name = "Delete Confirmation",
                                value = "deleted portfolio-123"
                            )
                        ]
                    )
                ]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Portfolio not found"
            )
        ]
    )
    fun deletePortfolio(
        @Parameter(
            description = "Portfolio identifier to delete",
            example = "portfolio-123"
        )
        @PathVariable id: String
    ): String {
        portfolioService.delete(id)
        return "deleted $id"
    }

    @GetMapping("/code/{code}")
    @Operation(
        summary = "Get portfolio by code",
        description = """
            Retrieves a portfolio by its code (human-readable identifier).

            Use this to:
            * Find portfolios using their code
            * Access portfolios with memorable names
            * Look up portfolios by external references
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Portfolio retrieved successfully"
            ),
            ApiResponse(
                responseCode = "404",
                description = "Portfolio not found"
            )
        ]
    )
    fun getPortfolioByCode(
        @Parameter(
            description = "Portfolio code",
            example = "MY_PORTFOLIO"
        )
        @PathVariable code: String
    ): PortfolioResponse = PortfolioResponse(portfolioService.findByCode(code))

    @PatchMapping(value = ["/{id}"])
    @Operation(
        summary = "Update portfolio",
        description = """
            Updates an existing portfolio with new information.
            Only the provided fields will be updated.

            Use this to:
            * Modify portfolio settings and metadata
            * Update portfolio names or descriptions
            * Change portfolio configuration
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Portfolio updated successfully"
            ),
            ApiResponse(
                responseCode = "404",
                description = "Portfolio not found"
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid portfolio data"
            )
        ]
    )
    fun savePortfolio(
        @Parameter(
            description = "Portfolio identifier to update",
            example = "portfolio-123"
        )
        @PathVariable id: String,
        @Parameter(
            description = "Updated portfolio data"
        )
        @RequestBody portfolio: PortfolioInput
    ): PortfolioResponse =
        PortfolioResponse(
            portfolioService.update(
                id,
                portfolio
            )
        )

    @PostMapping
    @Operation(
        summary = "Create portfolios",
        description = """
            Creates new portfolios.
            This endpoint handles bulk portfolio creation efficiently.

            Use this to:
            * Create new investment portfolios
            * Set up multiple portfolios in one request
            * Initialize portfolio structures
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Portfolios created successfully",
                content = [
                    Content(
                        mediaType = "application/json",
                        examples = [
                            ExampleObject(
                                name = "Portfolio Creation Response",
                                value = """
                                {
                                  "data": [
                                    {
                                      "id": "portfolio-123",
                                      "code": "NEW_PORTFOLIO",
                                      "name": "New Investment Portfolio",
                                      "currency": "USD"
                                    }
                                  ]
                                }
                                """
                            )
                        ]
                    )
                ]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid portfolio data"
            )
        ]
    )
    fun savePortfolios(
        @Parameter(
            description = "Portfolio creation request"
        )
        @RequestBody portfolio: PortfoliosRequest
    ): PortfoliosResponse = PortfoliosResponse(portfolioService.save(portfolio.data))

    @GetMapping(value = ["/asset/{assetId}"])
    @Operation(
        summary = "Find portfolios holding specific asset",
        description = """
            Finds all portfolios that hold a specific asset as of a given trade date.

            Use this to:
            * Identify which portfolios contain a specific asset
            * Track asset ownership across portfolios
            * Analyze asset distribution across portfolios
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Portfolios found successfully"
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid date format"
            )
        ]
    )
    fun getWhereHeld(
        @Parameter(
            description = "Asset identifier",
            example = "AAPL"
        )
        @PathVariable("assetId") assetId: String,
        @Parameter(
            description = "Trade date (YYYY-MM-DD format)",
            example = "2024-01-15"
        )
        @RequestParam(required = false) asAt: String = dateUtils.today()
    ): PortfoliosResponse =
        portfolioService.findWhereHeld(
            assetId,
            dateUtils.getFormattedDate(asAt)
        )

    @GetMapping(
        value = ["/export"],
        produces = [MediaType.TEXT_PLAIN_VALUE]
    )
    @Operation(
        summary = "Export portfolios to CSV",
        description = """
            Exports all portfolios owned by the authenticated user as a CSV file.
            Use this to backup or transfer your portfolio configurations.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "CSV file generated successfully"
            )
        ]
    )
    fun exportPortfolios(response: HttpServletResponse) {
        response.contentType = MediaType.TEXT_PLAIN_VALUE
        response.setHeader(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"portfolios.csv\""
        )
        val portfolios = portfolioService.portfolios()

        val csvWriter =
            CSVWriterBuilder(response.writer)
                .withSeparator(',')
                .build()
        csvWriter.writeNext(portfolioIoDefinition.headers(), false)
        for (portfolio in portfolios) {
            csvWriter.writeNext(portfolioIoDefinition.export(portfolio), false)
        }
        csvWriter.close()
    }

    @PostMapping(
        value = ["/import"],
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Import portfolios from CSV file",
        description = """
            Imports portfolios from a CSV file for the authenticated user.
            The CSV should have columns: Code, Name, Currency, Base.
            Existing portfolios with the same code will be skipped.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Portfolios imported successfully"
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid CSV format"
            )
        ]
    )
    fun importPortfolios(
        @Parameter(description = "CSV file containing portfolios to import")
        @RequestParam("file") file: MultipartFile
    ): PortfoliosResponse {
        val portfolioInputs = mutableListOf<PortfolioInput>()

        val csvParser = CSVParserBuilder().withSeparator(',').build()
        val csvReader =
            CSVReaderBuilder(file.inputStream.bufferedReader())
                .withCSVParser(csvParser)
                .build()

        csvReader.use { reader ->
            val lines = reader.readAll()
            if (lines.isEmpty()) {
                return PortfoliosResponse(emptyList())
            }

            val startIndex = if (portfolioIoDefinition.isHeaderRow(lines[0])) 1 else 0
            for (i in startIndex until lines.size) {
                val row = lines[i]
                if (row.isNotEmpty() && row[0].isNotBlank()) {
                    portfolioInputs.add(portfolioIoDefinition.parse(row))
                }
            }
        }

        return PortfoliosResponse(portfolioService.save(portfolioInputs))
    }
}