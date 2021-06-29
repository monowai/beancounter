package com.beancounter.client.ingest

import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.Asset

/**
 * Convert the incoming row to a Transaction object.
 *
 * @author mikeh
 * @since 2019-02-10
 */
interface TrnAdapter {
    fun from(trustedTrnImportRequest: TrustedTrnImportRequest?): TrnInput
    fun isValid(row: List<String>): Boolean
    fun resolveAsset(row: List<String>): Asset?
}
