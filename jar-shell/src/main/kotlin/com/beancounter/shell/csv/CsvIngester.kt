package com.beancounter.shell.csv

import com.beancounter.common.exception.SystemException
import com.beancounter.shell.ingest.AbstractIngester
import com.beancounter.shell.ingest.IngestionRequest
import com.beancounter.shell.ingest.TrnWriter
import com.opencsv.CSVReader
import com.opencsv.exceptions.CsvException
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.io.IOException
import java.io.Reader
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Import delimited files.
 * Ignores anything starting with a #
 */
@Service
class CsvIngester : AbstractIngester() {
    private var reader: Reader? = null
    private val log = LoggerFactory.getLogger(CsvIngester::class.java)

    override fun prepare(
        ingestionRequest: IngestionRequest,
        trnWriter: TrnWriter
    ) {
        val trimmedFile = ingestionRequest.file.trim { it <= ' ' }
        trnWriter.reset()
        trnWriter.flush()
        try {
            // Unit tests
            val file = ClassPathResource(trimmedFile).file
            reader = Files.newBufferedReader(Paths.get(file.toURI()))
        } catch (_: IOException) {
            try {
                // Runtime
                reader = Files.newBufferedReader(Paths.get(trimmedFile))
            } catch (ex: IOException) {
                log.error(ex.message)
            }
        }
        if (reader == null) {
            throw SystemException(
                String.format(
                    "Unable to resolve %s",
                    trimmedFile
                )
            )
        }
        log.info(
            "Import {}",
            trimmedFile
        )
    }

    // Skip header
    override val values: List<List<String>>
        get() {
            val results: MutableList<List<String>> = ArrayList()
            try {
                CSVReader(reader).use { csvReader ->
                    csvReader.skip(1) // Skip header
                    val iterator = csvReader.iterator()
                    while (iterator.hasNext()) {
                        val line = iterator.next()
                        if (!line[0].startsWith("#")) {
                            results.add(line.toList())
                        }
                    }
                }
            } catch (e: IOException) {
                throw SystemException(e.message!!, e)
            } catch (e: CsvException) {
                throw SystemException(e.message!!, e)
            }
            return results
        }
}