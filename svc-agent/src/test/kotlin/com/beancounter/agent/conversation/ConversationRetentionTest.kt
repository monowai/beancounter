package com.beancounter.agent.conversation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
import java.time.Duration

@DataJpaTest(
    includeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [ConversationService::class, ConversationRetention::class]
        )
    ],
    properties = ["agent.conversations.retention-days=90", "agent.conversations.purge-cron=-"]
)
@Import(ConversationServiceTest.ClockConfig::class)
class ConversationRetentionTest {
    @Autowired
    private lateinit var service: ConversationService

    @Autowired
    private lateinit var retention: ConversationRetention

    @Autowired
    private lateinit var clock: SteppingClock

    @Test
    fun `should purge only conversations idle beyond the configured retention days`() {
        val stale = service.create("me").id
        service.appendUser("me", stale, "old", false)
        clock.advance(Duration.ofDays(89))
        val recent = service.create("me").id
        service.appendUser("me", recent, "recent", false)
        clock.advance(Duration.ofDays(2))

        retention.purge()

        assertThat(service.list("me", 0, 20).map { it.id }).containsExactly(recent)
    }
}