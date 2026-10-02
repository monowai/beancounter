package com.beancounter.agent.conversation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Two writers on one conversation (two tabs, or a stream recording while
 * another request lands) must not lose a turn to a turn-number collision.
 */
@DataJpaTest(
    includeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [ConversationService::class])
    ],
    properties = [
        "spring.test.database.replace=none",
        "spring.datasource.url=jdbc:h2:mem:agent-concurrency;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
    ]
)
@Import(ConversationServiceTest.ClockConfig::class)
// Each append commits in its own transaction, as it does in production.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConversationConcurrencyTest {
    @Autowired
    private lateinit var service: ConversationService

    private val owner = "owner-concurrent"

    @AfterEach
    fun reset() {
        service.deleteAll(owner)
    }

    @Test
    fun `should keep every turn when two writers append to one conversation at once`() {
        val id = service.create(owner).id
        val writers = 4
        val turnsEach = 10
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(writers)
        val failures = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        repeat(writers) { w ->
            pool.submit {
                start.await()
                repeat(turnsEach) { t ->
                    runCatching { service.appendUser(owner, id, "w$w-$t", false) }.onFailure { failures.add(it) }
                }
            }
        }
        start.countDown()
        pool.shutdown()
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue()

        assertThat(failures).isEmpty()
        val turns = service.get(owner, id).messages
        assertThat(turns).hasSize(writers * turnsEach)
        assertThat(turns.map { it.content }.toSet()).hasSize(writers * turnsEach)
    }
}