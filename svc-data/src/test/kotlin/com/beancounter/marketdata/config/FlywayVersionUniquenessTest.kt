package com.beancounter.marketdata.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

/**
 * Two PRs can each add a migration with the same version under different names. Git merges
 * both without conflict and Flyway then refuses to start the service. Catch it in the build.
 */
class FlywayVersionUniquenessTest {
    @Test
    fun `should have one migration per Flyway version`() {
        val names =
            PathMatchingResourcePatternResolver()
                .getResources("classpath:db/migration/V*__*.sql")
                .mapNotNull { it.filename }
        assertThat(names).isNotEmpty

        val duplicates =
            names
                .groupBy { it.substringBefore("__") }
                .filterValues { it.size > 1 }

        assertThat(duplicates).isEmpty()
    }
}