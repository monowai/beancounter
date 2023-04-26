package com.beancounter.common.model

import com.beancounter.common.contracts.PriceRequest.Companion.dateUtils
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
    var callerId: String = "",
) : Serializable {

    companion object {
        @JvmStatic
        fun from(callerRef: CallerRef): CallerRef {
            val provider = callerRef.provider.ifBlank { "BC" }
            val batch = callerRef.batch.ifBlank {
                dateUtils.getDate().toString().replace("-", "")
            }
            val callerId = callerRef.callerId.ifBlank { KeyGenUtils().id }
            return CallerRef(provider, batch, callerId)
        }
    }
}
