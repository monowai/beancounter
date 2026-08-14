package com.beancounter.marketdata.isolation

import com.beancounter.marketdata.SpringMvcDbTest
import org.junit.jupiter.api.Test

/**
 * First half of the per-class isolation guard — see [SharedKeyIsolationFixture].
 * Meaningful only alongside [SharedKeyIsolationBTest]; running either alone proves
 * nothing, because the contended key is only contended when both have run.
 */
@SpringMvcDbTest
class SharedKeyIsolationATest : SharedKeyIsolationFixture() {
    @Test
    fun `should own its database when another class writes the same caller ref`() {
        saveTrnUnderContendedKey("ISOLATION-A")
    }
}