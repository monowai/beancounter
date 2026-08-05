package com.beancounter.marketdata.milestone

import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.UserExplorerAction
import com.beancounter.common.model.UserMilestone
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.registration.UserPreferencesService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional
import java.util.TimeZone

/**
 * Milestone/explorer-action stamps have to be resolved in the configured
 * `beancounter.zone`, not the JVM default — the pods run UTC while users are SGT, so
 * a bare `LocalDate.now()` earnedAt/recordedAt can be dated a day behind what the
 * user actually earned it on.
 *
 * Kiritimati (UTC+14) is always at least a day ahead of Midway (UTC-11), so these
 * assertions do not depend on the wall clock or on the host's zone.
 */
@ExtendWith(MockitoExtension::class)
class MilestoneServiceZoneTest {
    @Mock
    private lateinit var milestoneRepository: UserMilestoneRepository

    @Mock
    private lateinit var explorerActionRepository: UserExplorerActionRepository

    @Mock
    private lateinit var userPreferencesService: UserPreferencesService

    private val serviceZone = ZoneId.of("Pacific/Kiritimati")
    private val user = SystemUser(id = "user-123", email = "test@test.com")

    private fun serviceWithZone(): MilestoneService =
        MilestoneService(
            milestoneRepository,
            explorerActionRepository,
            userPreferencesService,
            DateUtils(serviceZone.id)
        )

    @Test
    fun `earnMilestone stamps a new milestone in the configured zone`() {
        val jvmDefault = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Midway"))
        try {
            whenever(
                milestoneRepository.findByOwnerAndMilestoneId(user, "portfolio-builder")
            ).thenReturn(Optional.empty())
            whenever(milestoneRepository.save(any<UserMilestone>())).thenAnswer { it.arguments[0] }

            val result = serviceWithZone().earnMilestone(user, "portfolio-builder", 1)

            assertThat(result.earnedAt).isEqualTo(LocalDate.now(serviceZone))
        } finally {
            TimeZone.setDefault(jvmDefault)
        }
    }

    @Test
    fun `earnMilestone stamps an upgraded tier in the configured zone`() {
        val jvmDefault = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Midway"))
        try {
            val existing =
                UserMilestone(
                    owner = user,
                    milestoneId = "portfolio-builder",
                    tier = 1,
                    earnedAt = LocalDate.now(serviceZone).minusDays(1)
                )
            whenever(
                milestoneRepository.findByOwnerAndMilestoneId(user, "portfolio-builder")
            ).thenReturn(Optional.of(existing))
            whenever(milestoneRepository.save(any<UserMilestone>())).thenAnswer { it.arguments[0] }

            val result = serviceWithZone().earnMilestone(user, "portfolio-builder", 2)

            assertThat(result.earnedAt).isEqualTo(LocalDate.now(serviceZone))
        } finally {
            TimeZone.setDefault(jvmDefault)
        }
    }

    @Test
    fun `recordExplorerAction stamps in the configured zone`() {
        val jvmDefault = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Midway"))
        try {
            whenever(explorerActionRepository.save(any<UserExplorerAction>())).thenAnswer { it.arguments[0] }

            val result = serviceWithZone().recordExplorerAction(user, "view:heatmap")

            assertThat(result.recordedAt).isEqualTo(LocalDate.now(serviceZone))
        } finally {
            TimeZone.setDefault(jvmDefault)
        }
    }
}