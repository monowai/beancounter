package com.beancounter.marketdata.payslip

import com.beancounter.auth.model.AuthConstants
import com.beancounter.marketdata.registration.SystemUserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * The caller's recurring payslip. Each user has at most one; every call reads or writes only
 * the caller's own row.
 */
@RestController
@RequestMapping("/payslips/recurring")
@CrossOrigin
@PreAuthorize(
    "hasAnyAuthority('${AuthConstants.SCOPE_USER}', '${AuthConstants.SCOPE_SYSTEM}')"
)
@Tag(name = "Payslips", description = "Monthly payslips recorded on the user's behalf")
class RecurringPayslipController(
    private val recurringPayslipService: RecurringPayslipService,
    private val systemUserService: SystemUserService
) {
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Get the caller's recurring payslip",
        description = "data is null when the caller has none."
    )
    fun get(): RecurringPayslipResponse =
        RecurringPayslipResponse(recurringPayslipService.get(systemUserService.getOrThrow()))

    @PutMapping(
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Schedule the caller's recurring payslip",
        description = """
            nextPayDate is the next payslip to post, stored as given; it must fall on dayOfMonth,
            or on the month's last day when the month is shorter. Replaces the caller's existing
            schedule. 400 when grossSalary <= 0, tax < 0, dayOfMonth is outside 1-31, nextPayDate
            is not on dayOfMonth, the portfolio is not the caller's or the cash asset does not
            exist.
        """
    )
    fun put(
        @RequestBody request: RecurringPayslipRequest
    ): RecurringPayslipResponse =
        RecurringPayslipResponse(recurringPayslipService.schedule(systemUserService.getOrThrow(), request))

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Stop the caller's recurring payslip", description = "Idempotent.")
    fun delete() = recurringPayslipService.stop(systemUserService.getOrThrow())
}