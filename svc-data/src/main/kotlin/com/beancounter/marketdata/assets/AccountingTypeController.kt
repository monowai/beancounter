package com.beancounter.marketdata.assets

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.AccountingTypeResponse
import com.beancounter.common.contracts.AccountingTypesResponse
import com.beancounter.marketdata.currency.CurrencyService
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class AccountingTypeCreateRequest(
    val category: String,
    val currency: String,
    val boardLot: Int = 1,
    val settlementDays: Int = 1
)

data class AccountingTypeUpdateRequest(
    val boardLot: Int? = null,
    val settlementDays: Int? = null
)

@RestController
@RequestMapping("/admin/accounting-types")
@CrossOrigin
@PreAuthorize(
    "hasAnyAuthority('" +
        AuthConstants.SCOPE_USER +
        "', '" +
        AuthConstants.SCOPE_SYSTEM +
        "')"
)
class AccountingTypeController(
    private val accountingTypeService: AccountingTypeService,
    private val currencyService: CurrencyService
) {
    @GetMapping
    fun findAll(): AccountingTypesResponse = AccountingTypesResponse(accountingTypeService.findAll())

    @GetMapping("/{id}")
    fun findById(
        @PathVariable id: String
    ): AccountingTypeResponse = AccountingTypeResponse(accountingTypeService.findById(id))

    @PostMapping
    fun create(
        @RequestBody request: AccountingTypeCreateRequest
    ): AccountingTypeResponse {
        val currency = currencyService.getCode(request.currency)
        return AccountingTypeResponse(
            accountingTypeService.getOrCreate(
                category = request.category,
                currency = currency,
                boardLot = request.boardLot,
                settlementDays = request.settlementDays
            )
        )
    }

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @RequestBody request: AccountingTypeUpdateRequest
    ): AccountingTypeResponse =
        AccountingTypeResponse(
            accountingTypeService.update(
                id = id,
                boardLot = request.boardLot,
                settlementDays = request.settlementDays
            )
        )

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: String
    ) = accountingTypeService.delete(id)
}