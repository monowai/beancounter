package com.beancounter.common.model

import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.KeyGenUtils
import jakarta.persistence.Embeddable

/**
 * Uniquely identifies a transaction, within a batch from a data provider.
 *
 * @author mikeh
 * @since 2019-02-10
 */
@Embeddable
data class CallerRef(
    // The System that owns the transaction
    var provider: String = "",
    var batch: String = "",
    var callerId: String = "",
) {
    /**
     * Helper methods to deal with CallerRef Objects
     */
    companion object {
        private const val DEFAULT_PROVIDER = "BC"
        private val dateUtils = DateUtils()
        private val keyGenUtils = KeyGenUtils()

        @JvmStatic
        fun from(callerRef: CallerRef): CallerRef {
            val provider = callerRef.provider.ifBlank { DEFAULT_PROVIDER }
            val batch =
                callerRef.batch.ifBlank {
                    dateUtils.getFormattedDate().toString().replace(
                        "-",
                        "",
                    )
                }
            val callerId = callerRef.callerId.ifBlank { keyGenUtils.id }
            return CallerRef(
                provider,
                batch,
                callerId,
            )
        }
    }
}
