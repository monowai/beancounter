package com.beancounter.client.ingest

import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnImportRequest

/**
 * Transform the TrustedTrnImportRequest payload into a writable TrnInput
 */
interface RowAdapter {
    fun transform(trustedTrnImportRequest: TrustedTrnImportRequest): TrnInput
}