package com.beancounter.shell.ingest

import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.ImportFormat
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.CallerRef
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.HashMap

@Service
abstract class AbstractIngester : Ingester {
    private val writers: MutableMap<String, TrnWriter> = HashMap()
    private var portfolioService: PortfolioServiceClient? = null

    @Autowired
    fun setPortfolioService(portfolioService: PortfolioServiceClient?) {
        this.portfolioService = portfolioService
    }

    @Autowired
    fun setTrnWriters(vararg trnWriter: TrnWriter) {
        var key = "DEFAULT"
        for (writer in trnWriter) {
            val writerId = writer.id()
            if (writerId != null) {
                key = writerId.toUpperCase()
            }
            writers[key.toUpperCase()] = writer
        }
    }

    private fun getWriter(id: String?): TrnWriter? {
        return writers[id!!.toUpperCase()]
    }

    /**
     * Default ingestion flow.
     *
     * @param ingestionRequest parameters to run the import.
     */
    override fun ingest(ingestionRequest: IngestionRequest) {
        val portfolio = portfolioService!!.getPortfolioByCode(ingestionRequest.portfolioCode!!)
        val writer = getWriter(ingestionRequest.writer)
            ?: throw BusinessException(String.format("Unable to resolve the Writer %s", ingestionRequest.writer))
        prepare(ingestionRequest, writer)
        val rows = values
        for ((i, row) in rows.withIndex()) {
            val callerRef = CallerRef(
                ingestionRequest.provider ?: portfolio.id, i.toString(), i.toString()
            )
            val trnRequest = TrustedTrnImportRequest(
                portfolio, ImportFormat.SHARESIGHT, callerRef, "", row
            )
            writer.write(trnRequest)
        }
        writer.flush()
    }

    abstract fun prepare(ingestionRequest: IngestionRequest?, trnWriter: TrnWriter?)
    abstract val values: List<List<String>>
}
