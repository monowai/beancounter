package com.beancounter.marketdata.trn

import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.PortfolioShare
import com.beancounter.common.model.ShareStatus
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.usdCashBalance
import com.beancounter.marketdata.portfolio.PortfolioShareRepository
import com.beancounter.marketdata.registration.SystemUserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate

/**
 * [UserTrnFinder] answers across the current user's portfolios: the ones they own and the ones
 * shared with them through an ACTIVE share.
 */
class UserTrnFinderTest {
    private val trnRepository = mock<TrnRepository>()
    private val systemUserService = mock<SystemUserService>()
    private val portfolioShareRepository = mock<PortfolioShareRepository>()
    private val trnPostProcessor = mock<TrnPostProcessor>()
    private val finder =
        UserTrnFinder(trnRepository, systemUserService, portfolioShareRepository, DateUtils(), trnPostProcessor)

    private val user = SystemUser(id = "adviser", email = "adviser@example.com")
    private val client = SystemUser(id = "client", email = "client@example.com")
    private val owned = Portfolio(id = "owned", code = "OWNED", owner = user)
    private val managed = Portfolio(id = "managed", code = "MANAGED", owner = client)
    private val asAt = LocalDate.of(2024, 1, 31)

    private fun proposed(
        id: String,
        portfolio: Portfolio
    ) = Trn(
        id = id,
        trnType = TrnType.DEPOSIT,
        asset = usdCashBalance,
        portfolio = portfolio,
        status = TrnStatus.PROPOSED
    )

    @BeforeEach
    fun setUp() {
        whenever(systemUserService.getOrThrow()).thenReturn(user)
        whenever(portfolioShareRepository.findBySharedWithAndStatus(eq(user), eq(ShareStatus.ACTIVE), any()))
            .thenReturn(
                listOf(
                    PortfolioShare(
                        portfolio = managed,
                        sharedWith = user,
                        createdBy = client,
                        status = ShareStatus.ACTIVE
                    )
                )
            )
        whenever(trnPostProcessor.postProcess(any<List<Trn>>())).thenAnswer { it.getArgument<List<Trn>>(0) }
    }

    @Test
    fun `ALL scope returns owned and managed proposed trns once each`() {
        val ownTrn = proposed("own", owned)
        val managedTrn = proposed("managed", managed)
        whenever(trnRepository.findByStatusAndPortfolioOwner(TrnStatus.PROPOSED, user, asAt))
            .thenReturn(listOf(ownTrn, managedTrn))
        whenever(trnRepository.findByStatusAndPortfolioIdIn(TrnStatus.PROPOSED, setOf("managed"), asAt))
            .thenReturn(listOf(managedTrn))

        assertThat(finder.findProposedForUser(ProposedScope.ALL, asAt))
            .extracting<String> { it.id }
            .containsExactlyInAnyOrder("own", "managed")
    }

    @Test
    fun `MANAGED scope leaves out trns the user only owns`() {
        whenever(trnRepository.findByStatusAndPortfolioIdIn(TrnStatus.PROPOSED, setOf("managed"), asAt))
            .thenReturn(listOf(proposed("managed", managed)))

        assertThat(finder.findProposedForUser(ProposedScope.MANAGED, asAt))
            .extracting<String> { it.id }
            .containsExactly("managed")
    }

    @Test
    fun `ALL scope count adds owned and managed counts`() {
        whenever(trnRepository.countByStatusAndPortfolioOwner(TrnStatus.PROPOSED, user, asAt)).thenReturn(2L)
        whenever(trnRepository.countByStatusAndPortfolioIdIn(TrnStatus.PROPOSED, setOf("managed"), asAt))
            .thenReturn(3L)

        assertThat(finder.countProposedForUser(ProposedScope.ALL, asAt)).isEqualTo(5L)
        assertThat(finder.countProposedForUser(ProposedScope.OWNED, asAt)).isEqualTo(2L)
    }
}