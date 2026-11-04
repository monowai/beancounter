package com.beancounter.marketdata.portfolio

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.input.PortfolioInput
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import com.opencsv.CSVWriterBuilder
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * CSV export and import of the caller's portfolio definitions, under the
 * same `/portfolios` path and authority as [PortfolioController].
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
class PortfolioCsvController internal constructor(
    private val portfolioService: PortfolioService,
    private val portfolioIoDefinition: PortfolioIoDefinition
) {
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