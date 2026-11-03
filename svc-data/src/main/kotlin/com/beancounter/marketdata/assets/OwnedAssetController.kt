package com.beancounter.marketdata.assets

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.AssetCategoryResponse
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetResponse
import com.beancounter.common.contracts.AssetSearchResponse
import com.beancounter.common.contracts.AssetStatusRequest
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.marketdata.providers.MarketDataService
import com.beancounter.marketdata.providers.PriceRefresh
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import com.opencsv.CSVWriterBuilder
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
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
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * The caller's own assets: list, update, delete, and CSV export and import.
 */
@RestController
@RequestMapping("/assets")
@CrossOrigin
@PreAuthorize(
    "hasAnyAuthority('" + AuthConstants.SCOPE_USER + "', '" + AuthConstants.SCOPE_SYSTEM + "')"
)
@Tag(
    name = "Assets",
    description = "Asset management operations"
)
class OwnedAssetController(
    private val ownedAssetService: OwnedAssetService,
    private val assetService: AssetService,
    private val assetIoDefinition: AssetIoDefinition
) {
    @GetMapping(
        value = ["/me"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get all assets owned by the current user",
        description = """
            Retrieves all assets owned by the authenticated user.
            Use this to find user-specific custom assets like bank accounts, real estate, etc.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Assets retrieved successfully",
                content = [Content(schema = Schema(implementation = AssetUpdateResponse::class))]
            )
        ]
    )
    fun getAllMyAssets(): AssetUpdateResponse = ownedAssetService.findByOwner()

    @GetMapping(
        value = ["/me/{category}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get assets owned by the current user filtered by category",
        description = """
            Retrieves all assets owned by the authenticated user with the specified category.
            Use this to find user-specific assets like bank accounts (ACCOUNT category).
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Assets retrieved successfully",
                content = [Content(schema = Schema(implementation = AssetUpdateResponse::class))]
            )
        ]
    )
    fun getMyAssets(
        @Parameter(description = "Asset category to filter by", example = "ACCOUNT")
        @PathVariable category: String
    ): AssetUpdateResponse = ownedAssetService.findByOwnerAndCategory(category)

    @DeleteMapping(
        value = ["/me/{assetId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Delete a user-owned asset",
        description = """
            Deletes an asset owned by the authenticated user.
            Only assets with a systemUser matching the current user can be deleted.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Asset deleted successfully"),
            ApiResponse(responseCode = "403", description = "Asset not owned by current user"),
            ApiResponse(responseCode = "404", description = "Asset not found")
        ]
    )
    fun deleteMyAsset(
        @Parameter(description = "Asset ID to delete", example = "abc123")
        @PathVariable assetId: String
    ) = ownedAssetService.deleteOwnedAsset(assetId)

    @PatchMapping(
        value = ["/me/{assetId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Update a user-owned asset",
        description = """
            Updates an asset owned by the authenticated user.
            Only assets with a systemUser matching the current user can be updated.
            Allows updating name and currency.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Asset updated successfully",
                content = [Content(schema = Schema(implementation = AssetResponse::class))]
            ),
            ApiResponse(responseCode = "403", description = "Asset not owned by current user"),
            ApiResponse(responseCode = "404", description = "Asset not found")
        ]
    )
    fun updateMyAsset(
        @Parameter(description = "Asset ID to update", example = "abc123")
        @PathVariable assetId: String,
        @RequestBody assetInput: AssetInput
    ): AssetResponse = AssetResponse(ownedAssetService.updateOwnedAsset(assetId, assetInput))

    @GetMapping(
        value = ["/me/export"],
        produces = [MediaType.TEXT_PLAIN_VALUE]
    )
    @Operation(
        summary = "Export user-owned assets to CSV",
        description = """
            Exports all assets owned by the authenticated user as a CSV file.
            Use this to backup or transfer your custom assets (accounts, real estate, etc.).
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
    fun exportAssets(response: HttpServletResponse) {
        response.contentType = MediaType.TEXT_PLAIN_VALUE
        response.setHeader(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"assets.csv\""
        )
        val assets = ownedAssetService.findByOwner().data.values

        val csvWriter =
            CSVWriterBuilder(response.writer)
                .withSeparator(',')
                .build()
        csvWriter.writeNext(assetIoDefinition.headers(), false)
        for (asset in assets) {
            csvWriter.writeNext(assetIoDefinition.export(asset), false)
        }
        csvWriter.close()
    }

    @PostMapping(
        value = ["/me/import"],
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Import assets from CSV file",
        description = """
            Imports assets from a CSV file for the authenticated user.
            The CSV should have columns: Code, Name, Category, Currency.
            Existing assets with the same code will be updated.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Assets imported successfully",
                content = [Content(schema = Schema(implementation = AssetUpdateResponse::class))]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid CSV format"
            )
        ]
    )
    fun importAssets(
        @Parameter(description = "CSV file containing assets to import")
        @RequestParam("file") file: MultipartFile
    ): AssetUpdateResponse {
        val owner = ownedAssetService.getCurrentOwnerId()
        val assetInputs = mutableMapOf<String, AssetInput>()

        val csvParser = CSVParserBuilder().withSeparator(',').build()
        val csvReader =
            CSVReaderBuilder(file.inputStream.bufferedReader())
                .withCSVParser(csvParser)
                .build()

        csvReader.use { reader ->
            val lines = reader.readAll()
            if (lines.isEmpty()) {
                return AssetUpdateResponse(emptyMap())
            }

            val startIndex = if (isHeaderRow(lines[0])) 1 else 0
            for (i in startIndex until lines.size) {
                val row = lines[i]
                if (row.isNotEmpty() && row[0].isNotBlank()) {
                    val assetInput = assetIoDefinition.parse(row, owner)
                    assetInputs[assetInput.code] = assetInput
                }
            }
        }

        return assetService.handle(AssetRequest(assetInputs))
    }

    private fun isHeaderRow(row: Array<String>): Boolean =
        row.isNotEmpty() &&
            row[0].equals(AssetIoDefinition.Columns.Code.name, ignoreCase = true)
}