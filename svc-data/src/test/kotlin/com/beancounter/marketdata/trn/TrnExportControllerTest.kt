package com.beancounter.marketdata.trn

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TrnExportControllerTest {
    @Test
    fun `sanitizeFilename strips CR LF to block header splitting`() {
        val malicious = "abc\r\nSet-Cookie: pwn=1"
        assertThat(TrnExportController.sanitizeFilename(malicious))
            .doesNotContain("\r")
            .doesNotContain("\n")
            .isEqualTo("abc__Set-Cookie__pwn_1")
    }

    @Test
    fun `sanitizeFilename keeps safe characters`() {
        assertThat(TrnExportController.sanitizeFilename("portfolio-123_demo.v2"))
            .isEqualTo("portfolio-123_demo.v2")
    }

    @Test
    fun `sanitizeFilename replaces path traversal and quoting characters`() {
        assertThat(TrnExportController.sanitizeFilename("../etc/passwd\""))
            .isEqualTo(".._etc_passwd_")
    }
}