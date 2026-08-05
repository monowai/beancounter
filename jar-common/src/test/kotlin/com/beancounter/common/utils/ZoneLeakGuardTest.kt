package com.beancounter.common.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Guards the zone boundary across the mono-repo.
 *
 * Reading the clock with a bare `LocalDate.now()` resolves in the JVM default zone
 * (UTC in the pods) rather than the configured `beancounter.zone` the users actually
 * live in. That leak rejected same-day trade dates on the CSV import path for eight
 * hours a day before anyone noticed, because the failure was swallowed by the
 * consumer. Dates that reach a user, a ledger row, or a query bound must come from
 * [DateUtils].
 *
 * The baseline resource lists the files that still leak. It only ever shrinks: a new
 * leak fails this test, and so does a stale entry, so the list can't quietly rot into
 * a permanent allowlist.
 */
class ZoneLeakGuardTest {
    private val bannedCall =
        Regex(
            """\b(?:LocalDate|LocalDateTime|LocalTime|YearMonth|ZonedDateTime|OffsetDateTime)\.now\(\)""" +
                """|ZoneId\.systemDefault\(\)"""
        )
    private val blockComment = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
    private val lineComment = Regex("""//[^\n]*""")

    @Test
    fun `no new zone leaks in production sources`() {
        val leaking = leakingFiles()
        val baseline = baseline()

        assertThat(leaking - baseline)
            .describedAs(
                "These files read the clock in the JVM default zone. Inject DateUtils and " +
                    "use dateUtils.date / dateUtils.today(), or LocalDate.now(dateUtils.zoneId)"
            ).isEmpty()
    }

    @Test
    fun `baseline lists no file that has already been fixed`() {
        val leaking = leakingFiles()
        val baseline = baseline()

        assertThat(baseline - leaking)
            .describedAs("Fixed — delete these lines from zone-leak-baseline.txt")
            .isEmpty()
    }

    private fun baseline(): Set<String> =
        javaClass
            .getResourceAsStream("/zone-leak-baseline.txt")!!
            .bufferedReader()
            .readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()

    /**
     * Repo-relative paths of production sources containing a banned call, comments
     * stripped so that documenting the pattern doesn't count as using it.
     */
    private fun leakingFiles(): Set<String> {
        val root = repoRoot()
        return root
            .listFiles { file -> file.isDirectory }
            .orEmpty()
            .map { module -> File(module, "src/main/kotlin") }
            .filter { it.isDirectory }
            .flatMap { it.walkTopDown().filter { file -> file.extension == "kt" } }
            .filter { file ->
                val code = lineComment.replace(blockComment.replace(file.readText(), ""), "")
                bannedCall.containsMatchIn(code)
            }.map { it.relativeTo(root).path }
            .toSet()
    }

    private fun repoRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null && !File(candidate, "settings.gradle.kts").isFile) {
            candidate = candidate.parentFile
        }
        return candidate
            ?: error("Could not locate the repository root from ${System.getProperty("user.dir")}")
    }
}