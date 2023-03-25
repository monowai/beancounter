package com.beancounter.shell.google

import com.beancounter.shell.ingest.AbstractIngester
import com.beancounter.shell.ingest.IngestionRequest
import com.beancounter.shell.ingest.TrnWriter
import com.google.api.services.sheets.v4.Sheets
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * Reads the actual google sheet.
 *
 * @author mikeh
 * @since 2019-02-08
 */
@Service
class SheetIngester : AbstractIngester() {
    private val log = LoggerFactory.getLogger(SheetIngester::class.java)
    private lateinit var googleGateway: GoogleGateway
    private var service: Sheets? = null
    private var ingestionRequest: IngestionRequest? = null

    @Value("\${range:All Trades Report}")
    private val range: String? = null

    @Autowired
    fun setGoogleTransport(googleGateway: GoogleGateway) {
        this.googleGateway = googleGateway
    }

    override fun prepare(ingestionRequest: IngestionRequest, trnWriter: TrnWriter) {
        service = googleGateway.getSheets(googleGateway.httpTransport)
        this.ingestionRequest = ingestionRequest
    }

    override val values: List<List<String>>
        get() {
            log.info("Processing {} {}", range, ingestionRequest!!.file)
            val results: MutableList<List<String>> = ArrayList()
            val sheetResults = googleGateway.getValues(
                service!!,
                ingestionRequest!!.file,
                range,
            )
            for (sheetResult in sheetResults) {
                results.add(toStrings(sheetResult))
            }
            return results
        }

    private fun toStrings(sheetResult: List<Any>): List<String> {
        val result: MutableList<String> = ArrayList()
        for (o in sheetResult) {
            result.add(o.toString())
        }
        return result
    }
}
