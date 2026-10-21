package com.beancounter.agent.review

import com.beancounter.agent.conversation.ConversationServiceTest
import com.beancounter.agent.conversation.SteppingClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
import java.time.LocalDate

@DataJpaTest(
    includeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [AssetReviewCacheService::class])
    ],
    properties = ["beancounter.agent.review-cache.enabled=false"]
)
@Import(ConversationServiceTest.ClockConfig::class)
class AssetReviewCacheDisabledTest {
    @Autowired
    private lateinit var cache: AssetReviewCacheService

    @Autowired
    private lateinit var repository: AssetReviewRepository

    @Autowired
    private lateinit var clock: SteppingClock

    @Test
    fun `a disabled cache should neither store nor serve reviews`() {
        val key = AssetReviewKey("NASDAQ", "NATO", LocalDate.now(clock), "smart")

        cache.store(key, "NATO looks steady.")

        assertThat(cache.enabled).isFalse()
        assertThat(repository.findAll()).isEmpty()
        assertThat(cache.find(key)).isNull()
    }
}