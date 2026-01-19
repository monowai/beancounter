package com.beancounter.marketdata.registration

import com.beancounter.auth.model.AuthConstants
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Controller for user offboarding - allows users to remove their data from Beancounter.
 */
@RestController
@RequestMapping("/offboard")
@PreAuthorize("hasAuthority('" + AuthConstants.SCOPE_USER + "')")
class OffboardingController(
    private val offboardingService: OffboardingService
) {
    /**
     * Get summary of user's data for offboarding preview.
     */
    @GetMapping("/summary")
    fun getSummary(): OffboardingSummaryResponse = offboardingService.getSummary()

    /**
     * Delete all user-owned assets and their associated data.
     */
    @DeleteMapping("/assets")
    fun deleteAssets(): OffboardingResult = offboardingService.deleteUserAssets()

    /**
     * Delete all user's portfolios and their transactions.
     */
    @DeleteMapping("/portfolios")
    fun deletePortfolios(): OffboardingResult = offboardingService.deleteUserPortfolios()

    /**
     * Delete all user's wealth data (portfolios, assets, and transactions).
     */
    @DeleteMapping("/wealth")
    fun deleteWealth(): OffboardingResult = offboardingService.deleteUserWealth()

    /**
     * Delete entire user account including all data.
     */
    @DeleteMapping("/account")
    fun deleteAccount(): OffboardingResult = offboardingService.deleteUserAccount()
}