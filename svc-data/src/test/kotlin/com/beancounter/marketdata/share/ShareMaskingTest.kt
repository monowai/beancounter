package com.beancounter.marketdata.share

import com.beancounter.common.model.SystemUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * A share shows the caller their own email and masks everyone else's.
 */
class ShareMaskingTest {
    @Test
    fun `maskEmail masks middle characters of local part`() {
        assertThat(maskEmail("mike@gmail.com"))
            .isEqualTo("m**e@gmail.com")
    }

    @Test
    fun `maskEmail handles short local parts`() {
        assertThat(maskEmail("ab@gmail.com"))
            .isEqualTo("a*@gmail.com")
    }

    @Test
    fun `maskEmail handles single character local part`() {
        assertThat(maskEmail("a@gmail.com"))
            .isEqualTo("a@gmail.com")
    }

    @Test
    fun `maskEmail handles long email addresses`() {
        val masked = maskEmail("longusername@example.com")
        assertThat(masked).startsWith("l")
        assertThat(masked).endsWith("e@example.com")
        assertThat(masked).contains("*")
    }

    @Test
    fun `maskUnlessCaller masks another user's email and leaves the caller's`() {
        val caller = SystemUser(id = "caller", email = "caller@testing.com")
        val other = SystemUser(id = "other", email = "other@testing.com")

        assertThat(maskUnlessCaller(other, caller).email).isEqualTo("o***r@testing.com")
        assertThat(maskUnlessCaller(caller, caller).email).isEqualTo("caller@testing.com")
    }
}