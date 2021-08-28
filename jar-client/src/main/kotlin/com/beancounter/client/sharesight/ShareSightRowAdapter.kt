package com.beancounter.client.sharesight

import com.beancounter.client.ingest.RowAdapter
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnImportRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * Convert a sharesight exported row to a BeanCounter standardised row.
 */
@Service
class ShareSightRowAdapter : RowAdapter {
    private lateinit var shareSightFactory: ShareSightFactory

    @Autowired
    fun setShareSightFactory(shareSightFactory: ShareSightFactory) {
        this.shareSightFactory = shareSightFactory
    }

    override fun transform(trustedTrnImportRequest: TrustedTrnImportRequest): TrnInput {
        val trnAdapter = shareSightFactory.adapter(trustedTrnImportRequest.row)
        if (trnAdapter.isValid(trustedTrnImportRequest.row)) {
            return trnAdapter.from(trustedTrnImportRequest)
        }
        throw BusinessException(
            String.format(
                "Unable to transform %s using ",
                trustedTrnImportRequest.toString(),
                trnAdapter.javaClass.name
            )
        )
    }
}
