package com.beancounter.marketdata.persistence

import org.hibernate.exception.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.sql.SQLException

/**
 * Saves rows that carry a deterministic or natural-key unique constraint without
 * failing the caller's request when a concurrent writer has already inserted the
 * same row(s) between the caller's check and its write.
 *
 * Contract:
 *  - Fast path: the whole batch is saved + flushed in its own REQUIRES_NEW
 *    transaction. If nothing conflicts, every row is written and returned.
 *  - On a unique-constraint violation, that transaction is rolled back (a failed
 *    flush leaves the Hibernate session unusable, so we never touch it again) and
 *    the rows are retried one at a time, each in its own fresh REQUIRES_NEW
 *    transaction. Rows that individually conflict are skipped; every other row is
 *    written. The rows that were actually written are returned.
 *  - Every persistence attempt is isolated in its own transaction so a failed
 *    flush can never poison the caller's own transaction / persistence context.
 */
@Service
class ConflictTolerantWriter(
    transactionManager: PlatformTransactionManager
) {
    private val log = LoggerFactory.getLogger(ConflictTolerantWriter::class.java)

    private val txn =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

    fun <T : Any> saveAll(
        repo: JpaRepository<T, *>,
        rows: List<T>
    ): List<T> {
        if (rows.isEmpty()) return rows
        return try {
            txn.executeWithoutResult { repo.saveAllAndFlush(rows) }
            rows
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: RuntimeException
        ) {
            if (isUniqueViolation(e)) {
                retryRowByRow(repo, rows, e.message)
            } else {
                throw e
            }
        }
    }

    private fun <T : Any> retryRowByRow(
        repo: JpaRepository<T, *>,
        rows: List<T>,
        batchFailureMessage: String?
    ): List<T> {
        log.info(
            "Unique-constraint conflict detected, retrying {} row(s) individually: {}",
            rows.size,
            batchFailureMessage
        )
        return rows.mapNotNull { row -> saveRow(repo, row) }
    }

    private fun <T : Any> saveRow(
        repo: JpaRepository<T, *>,
        row: T
    ): T? =
        try {
            // Return the caller's own instance, exactly as the fast path does - never
            // the merged/managed copy saveAndFlush hands back for an assigned-id row.
            txn.executeWithoutResult { repo.saveAndFlush(row) }
            row
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: RuntimeException
        ) {
            if (isUniqueViolation(e)) {
                log.info("Skipping row that already exists (unique-constraint conflict): {}", e.message)
                null
            } else {
                throw e
            }
        }

    /**
     * Only a unique-constraint violation is a benign concurrent-writer conflict; every
     * other constraint failure (NOT NULL, foreign-key, check) or unrelated data-access
     * error (e.g. a dropped connection) is a real bug and must propagate.
     *
     * Walks the cause chain looking for either:
     *  - a Hibernate [ConstraintViolationException] whose
     *    [ConstraintViolationException.getKind] is
     *    [ConstraintViolationException.ConstraintKind.UNIQUE], or
     *  - a [SQLException] whose `sqlState` is `23505` (SQLSTATE `unique_violation`,
     *    shared by Postgres and H2) — covers the case where Hibernate's own wrapper
     *    is absent, e.g. a raw [jakarta.persistence.PersistenceException] wrapping a
     *    [java.sql.SQLIntegrityConstraintViolationException].
     */
    private fun isUniqueViolation(e: Throwable): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            if (cause is ConstraintViolationException &&
                cause.kind == ConstraintViolationException.ConstraintKind.UNIQUE
            ) {
                return true
            }
            // A non-UNIQUE (or unclassified) kind is not a verdict: keep walking so
            // the SQLSTATE fallback below can still recognise the violation.
            if (cause is SQLException && cause.sqlState == "23505") {
                return true
            }
            cause = cause.cause
        }
        return false
    }
}