package com.beancounter.common.model

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
    var provider: String? = null,
    var batch: String? = null,
    var callerId: String? = null
) : Serializable {

    companion object {
        @JvmStatic
        fun from(callerRef: CallerRef?, portfolio: Portfolio?): CallerRef {

            val provider = if (callerRef?.provider == null) "BC" else callerRef.provider!!
            val batch = if (callerRef?.batch == null) portfolio?.code ?: "-" else callerRef.batch!!
            val callerId = if (callerRef?.callerId == null) KeyGenUtils().id else callerRef.callerId!!
            return CallerRef(provider, batch, callerId)
        }
    }
}
