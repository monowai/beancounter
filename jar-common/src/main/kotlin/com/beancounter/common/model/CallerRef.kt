package com.beancounter.common.model

import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.KeyGenUtils
import jakarta.persistence.Embeddable
import java.io.Serializable

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
) : Serializable {
    companion object {
        @JvmStatic
        fun from(callerRef: CallerRef): CallerRef {
            val provider = callerRef.provider.ifBlank { "BC" }
            val batch =
                callerRef.batch.ifBlank {
                    DateUtils().getFormattedDate().toString().replace("-", "")
                }
            val callerId = callerRef.callerId.ifBlank { KeyGenUtils().id }
            return CallerRef(provider, batch, callerId)
        }
    }
}
