package com.beancounter.common.model

import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.utils.KeyGenUtils
import java.io.Serializable
import javax.persistence.Embeddable

/**
 * Uniquely identifies a transaction, within a batch from a data provider.
 *
 * @author mikeh
 * @since 2019-02-10
 */
@Embeddable
data class CallerRef(
    var provider: String = "",
    var batch: String = "",
    var callerId: String = ""
) : Serializable {

    companion object {
        @JvmStatic
        fun from(callerRef: CallerRef, portfolio: Portfolio): CallerRef {
            val provider = if (callerRef.provider.isBlank()) "BC" else callerRef.provider
            val batch = callerRef.batch.ifBlank { portfolio.code }
            val callerId = if (callerRef.callerId.isBlank()) KeyGenUtils().id else callerRef.callerId
            return CallerRef(provider, batch, callerId)
        }
    }
}
