package com.beancounter.shell.google

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.SystemException
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ValueRange
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Google gateway
 */
@Service
class GoogleGateway internal constructor(private val googleAuthConfig: GoogleAuthConfig) {
    private val log = LoggerFactory.getLogger(GoogleGateway::class.java)

    val httpTransport: NetHttpTransport
        get() = try {
            GoogleNetHttpTransport.newTrustedTransport()
        } catch (e: GeneralSecurityException) {
            throw SystemException(e.message)
        } catch (e: IOException) {
            throw SystemException(e.message)
        }

    fun getSheets(httpTransport: NetHttpTransport?): Sheets {
        return Sheets.Builder(
            httpTransport,
            GsonFactory.getDefaultInstance(),
            googleAuthConfig.getCredentials(httpTransport)
        )
            .setApplicationName("BeanCounter")
            .build()
    }

    fun getValues(service: Sheets, sheetId: String?, range: String?): List<List<Any>> {
        val response: ValueRange = try {
            service.spreadsheets()
                .values()[sheetId, range]
                .execute()
        } catch (e: IOException) {
            throw SystemException(e.message)
        }
        val values = response.getValues()
        if (values == null || values.isEmpty()) {
            log.error("No data found.")
            throw BusinessException(String.format("No data found for %s %s", sheetId, range))
        }
        return values
    }
}
