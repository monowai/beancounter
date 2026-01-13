package com.beancounter.marketdata.tax

import com.beancounter.auth.model.AuthConstants
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for managing user-defined tax rates by country.
 *
 * Each user can configure their own income tax rates for different countries
 * where they hold income-generating assets (e.g., rental properties).
 */
@RestController
@RequestMapping("/tax-rates")
@CrossOrigin
@PreAuthorize(
    "hasAnyAuthority('${AuthConstants.SCOPE_USER}', '${AuthConstants.SCOPE_SYSTEM}')"
)
@Tag(
    name = "Tax Rates",
    description = "Configure income tax rates by country for retirement income calculations"
)
class TaxRateController(
    private val taxRateService: TaxRateService
) {
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Get all tax rates for current user",
        description = "Returns all country tax rates configured by the authenticated user"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Tax rates retrieved successfully")
        ]
    )
    fun getMyTaxRates(): TaxRatesResponse = taxRateService.getMyTaxRates()

    @GetMapping(
        value = ["/{countryCode}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get tax rate for a specific country",
        description = "Returns the tax rate for a specific country (ISO 3166-1 alpha-2 code)"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Tax rate retrieved successfully"),
            ApiResponse(responseCode = "404", description = "Tax rate not configured for this country")
        ]
    )
    fun getTaxRate(
        @Parameter(description = "Country code (ISO 3166-1 alpha-2)", example = "NZ")
        @PathVariable countryCode: String
    ): TaxRateResponse? = taxRateService.getTaxRate(countryCode)

    @PostMapping(
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Save tax rate for a country",
        description = """
            Creates or updates a tax rate for a country.

            The rate is expressed as a decimal (e.g., 0.20 for 20%).
            Country code must be ISO 3166-1 alpha-2 (e.g., NZ, SG, AU).

            This rate is used when calculating net income for assets with
            deductIncomeTax=true in their PrivateAssetConfig.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Tax rate saved successfully"),
            ApiResponse(responseCode = "400", description = "Invalid country code or rate value")
        ]
    )
    fun saveTaxRate(
        @RequestBody request: TaxRateRequest
    ): TaxRateResponse = taxRateService.saveTaxRate(request)

    @DeleteMapping(value = ["/{countryCode}"])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Delete tax rate for a country",
        description = "Removes the tax rate configuration for a country"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Tax rate deleted successfully"),
            ApiResponse(responseCode = "404", description = "Tax rate not found for this country")
        ]
    )
    fun deleteTaxRate(
        @Parameter(description = "Country code (ISO 3166-1 alpha-2)", example = "NZ")
        @PathVariable countryCode: String
    ) = taxRateService.deleteTaxRate(countryCode)
}