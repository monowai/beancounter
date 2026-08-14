package com.beancounter.marketdata

import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.test.context.ContextConfigurationAttributes
import org.springframework.test.context.ContextCustomizer
import org.springframework.test.context.ContextCustomizerFactory
import org.springframework.test.context.MergedContextConfiguration

/**
 * Gives every [SpringMvcDbTest] class its own H2 database.
 *
 * The h2db profile pins `jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`,
 * which is one database for the whole JVM that outlives context teardown. All 61
 * `@SpringMvcDbTest` classes therefore wrote into the same tables, and rows left by
 * one class were visible to the next (#1081).
 *
 * That is worse than untidy fixtures, because some constraints are global rather
 * than owner- or portfolio-scoped. `Trn` is unique on `(provider, batch, callerId)`,
 * and `CallerRef.from` defaults a blank provider to `BC` and a blank batch to today's
 * date — so `CallerRef(callerId = "1")` in two unrelated classes is the same key, and
 * whichever committed second got a 409. The failure surfaces as flakiness but is
 * strictly order-dependent.
 *
 * Isolation is free here: `@SpringMvcDbTest` carries `@DirtiesContext(AFTER_CLASS)`,
 * so each class already builds and discards its own context. Naming the database
 * after the test class means the schema is rebuilt into a fresh database rather than
 * inherited, without adding a single context build.
 */
class H2PerClassContextCustomizerFactory : ContextCustomizerFactory {
    override fun createContextCustomizer(
        testClass: Class<*>,
        configAttributes: List<ContextConfigurationAttributes>
    ): ContextCustomizer? =
        if (testClass.isAnnotationPresent(SpringMvcDbTest::class.java)) {
            H2PerClassContextCustomizer(testClass.name)
        } else {
            // Slice tests and plain @SpringBootTest classes share contexts by design;
            // handing them a unique datasource would fragment the context cache for
            // no benefit, since they are not the ones writing to the shared database.
            null
        }
}

/**
 * Overrides `spring.datasource.url` with a database named for the test class.
 *
 * [equals]/[hashCode] are part of the context cache key, so they must reflect the
 * database name — two classes with different databases must never share a context.
 */
private class H2PerClassContextCustomizer(
    private val testClassName: String
) : ContextCustomizer {
    override fun customizeContext(
        context: ConfigurableApplicationContext,
        mergedConfig: MergedContextConfiguration
    ) {
        // addFirst: the h2db profile yaml sets the same key, and the per-class value
        // has to win.
        context.environment.propertySources.addFirst(
            MapPropertySource(
                PROPERTY_SOURCE_NAME,
                mapOf(
                    "spring.datasource.url" to
                        "jdbc:h2:mem:$testClassName;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL"
                )
            )
        )
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is H2PerClassContextCustomizer && testClassName == other.testClassName)

    override fun hashCode(): Int = testClassName.hashCode()

    private companion object {
        const val PROPERTY_SOURCE_NAME = "h2PerClassDatasource"
    }
}