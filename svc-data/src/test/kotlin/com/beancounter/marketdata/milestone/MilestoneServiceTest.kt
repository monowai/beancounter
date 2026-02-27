package com.beancounter.marketdata.milestone

import com.beancounter.common.model.MilestoneMode
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.UserExplorerAction
import com.beancounter.common.model.UserMilestone
import com.beancounter.common.model.UserPreferences
import com.beancounter.marketdata.registration.UserPreferencesService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

class MilestoneServiceTest {
    private lateinit var milestoneRepository: UserMilestoneRepository
    private lateinit var explorerActionRepository: UserExplorerActionRepository
    private lateinit var userPreferencesService: UserPreferencesService
    private lateinit var milestoneService: MilestoneService

    private val user = SystemUser(id = "user-123", email = "test@test.com")

    @BeforeEach
    fun setUp() {
        milestoneRepository = mock()
        explorerActionRepository = mock()
        userPreferencesService = mock()
        milestoneService =
            MilestoneService(
                milestoneRepository,
                explorerActionRepository,
                userPreferencesService
            )
    }

    @Test
    fun `getEarnedMilestones returns all milestones for user`() {
        val milestones =
            listOf(
                UserMilestone(owner = user, milestoneId = "portfolio-builder", tier = 1),
                UserMilestone(owner = user, milestoneId = "first-steps", tier = 2)
            )
        whenever(milestoneRepository.findByOwner(user)).thenReturn(milestones)

        val result = milestoneService.getEarnedMilestones(user)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.milestoneId })
            .containsExactlyInAnyOrder("portfolio-builder", "first-steps")
    }

    @Test
    fun `earnMilestone creates new milestone when none exists`() {
        whenever(
            milestoneRepository.findByOwnerAndMilestoneId(user, "portfolio-builder")
        ).thenReturn(Optional.empty())
        whenever(milestoneRepository.save(any<UserMilestone>())).thenAnswer {
            it.arguments[0]
        }

        val result = milestoneService.earnMilestone(user, "portfolio-builder", 1)

        assertThat(result.milestoneId).isEqualTo("portfolio-builder")
        assertThat(result.tier).isEqualTo(1)
    }

    @Test
    fun `earnMilestone upgrades tier when new tier is higher`() {
        val existing =
            UserMilestone(owner = user, milestoneId = "portfolio-builder", tier = 1)
        whenever(
            milestoneRepository.findByOwnerAndMilestoneId(user, "portfolio-builder")
        ).thenReturn(Optional.of(existing))
        whenever(milestoneRepository.save(any<UserMilestone>())).thenAnswer {
            it.arguments[0]
        }

        val result = milestoneService.earnMilestone(user, "portfolio-builder", 2)

        assertThat(result.tier).isEqualTo(2)
        verify(milestoneRepository).save(any())
    }

    @Test
    fun `earnMilestone does not downgrade tier`() {
        val existing =
            UserMilestone(owner = user, milestoneId = "portfolio-builder", tier = 3)
        whenever(
            milestoneRepository.findByOwnerAndMilestoneId(user, "portfolio-builder")
        ).thenReturn(Optional.of(existing))

        val result = milestoneService.earnMilestone(user, "portfolio-builder", 1)

        assertThat(result.tier).isEqualTo(3)
        verify(milestoneRepository, never()).save(any())
    }

    @Test
    fun `earnMilestone does not change tier when same`() {
        val existing =
            UserMilestone(owner = user, milestoneId = "portfolio-builder", tier = 2)
        whenever(
            milestoneRepository.findByOwnerAndMilestoneId(user, "portfolio-builder")
        ).thenReturn(Optional.of(existing))

        val result = milestoneService.earnMilestone(user, "portfolio-builder", 2)

        assertThat(result.tier).isEqualTo(2)
        verify(milestoneRepository, never()).save(any())
    }

    @Test
    fun `getExplorerActions returns all actions for user`() {
        val actions =
            listOf(
                UserExplorerAction(owner = user, actionId = "view:heatmap"),
                UserExplorerAction(owner = user, actionId = "view:table")
            )
        whenever(explorerActionRepository.findByOwner(user)).thenReturn(actions)

        val result = milestoneService.getExplorerActions(user)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.actionId })
            .containsExactlyInAnyOrder("view:heatmap", "view:table")
    }

    @Test
    fun `recordExplorerAction saves new action`() {
        whenever(explorerActionRepository.save(any<UserExplorerAction>())).thenAnswer {
            it.arguments[0]
        }

        val result = milestoneService.recordExplorerAction(user, "view:heatmap")

        assertThat(result.actionId).isEqualTo("view:heatmap")
    }

    @Test
    fun `getMilestoneMode returns mode from user preferences`() {
        val prefs =
            UserPreferences(
                owner = user,
                milestoneMode = MilestoneMode.SILENT
            )
        whenever(userPreferencesService.getOrCreate(user)).thenReturn(prefs)

        val result = milestoneService.getMilestoneMode(user)

        assertThat(result).isEqualTo(MilestoneMode.SILENT)
    }

    @Test
    fun `getMilestoneMode defaults to ACTIVE`() {
        val prefs = UserPreferences(owner = user)
        whenever(userPreferencesService.getOrCreate(user)).thenReturn(prefs)

        val result = milestoneService.getMilestoneMode(user)

        assertThat(result).isEqualTo(MilestoneMode.ACTIVE)
    }
}