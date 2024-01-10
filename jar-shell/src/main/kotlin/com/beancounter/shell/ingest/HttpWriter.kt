package com.beancounter.shell.ingest

import com.beancounter.client.ingest.FxTransactions
import com.beancounter.client.ingest.RowAdapter
import com.beancounter.client.services.TrnService
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.Portfolio
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Write to the BC HTTP api.
 */
@Service
class HttpWriter(
    private val trnService: TrnService,
    private val rowAdapter: RowAdapter,
    private val fxTransactions: FxTransactions,
) : TrnWriter {
    private val log = LoggerFactory.getLogger(HttpWriter::class.java)
    var trnInputs: MutableCollection<TrnInput> = ArrayList()
    private var portfolio: Portfolio? = null

    override fun reset() {
        portfolio = null
        trnInputs = ArrayList()
    }

    override fun write(trnRequest: TrustedTrnImportRequest) {
        portfolio = trnRequest.portfolio
        val trnInput = rowAdapter.transform(trnRequest)
        trnInputs.add(trnInput)
    }

    override fun flush() {
        val rows: Int
        if (!trnInputs.isEmpty()) {
            log.info("Back filling FX rates...")
            rows = trnInputs.size
            for (trnInput in trnInputs) {
                fxTransactions.setRates(portfolio!!, trnInput)
            }
            log.info(
                "Writing {} transactions to portfolio {}",
                rows,
                portfolio!!.code,
            )
            if (portfolio != null) {
                val trnRequest =
                    TrnRequest(
                        portfolio!!.id,
                        trnInputs.toTypedArray(),
                    )
                val (data) = trnService.write(trnRequest)
                log.info("Wrote {}", data.size)
            }
            log.info("Complete!")
        }
        reset()
    }

    override fun id(): String {
        return "HTTP"
    }
}
