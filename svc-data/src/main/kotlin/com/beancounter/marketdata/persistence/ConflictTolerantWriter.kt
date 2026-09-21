package com.beancounter.marketdata.persistence

import org.hibernate.exception.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

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
            txn.execute { repo.saveAllAndFlush(rows) }
            rows
        } catch (e: DataIntegrityViolationException) {
            retryRowByRow(repo, rows, e.message)
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: RuntimeException
        ) {
            if (isConstraintViolation(e)) {
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
            "Concurrent insert detected, retrying {} row(s) individually: {}",
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
            txn.execute { repo.saveAndFlush(row) }
        } catch (e: DataIntegrityViolationException) {
            log.info("Skipping row that conflicted with a concurrent writer: {}", e.message)
            null
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: RuntimeException
        ) {
            if (isConstraintViolation(e)) {
                log.info("Skipping row that conflicted with a concurrent writer: {}", e.message)
                null
            } else {
                throw e
            }
        }

    /**
     * Under H2 (tests) the constraint failure can surface as a raw
     * [ConstraintViolationException] / [jakarta.persistence.PersistenceException]
     * instead of Spring's translated [DataIntegrityViolationException]. Only treat
     * it as a benign conflict when a Hibernate [ConstraintViolationException] is
     * somewhere in the cause chain — anything else propagates.
     */
    private fun isConstraintViolation(e: Throwable): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            if (cause is ConstraintViolationException) return true
            cause = cause.cause
        }
        return false
    }
}