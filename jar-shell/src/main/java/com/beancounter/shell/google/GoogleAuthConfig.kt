package com.beancounter.shell.google

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.sheets.v4.SheetsScopes
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

/**
 * Encapsulates config props to connect with Google API and perform authentication checks.
 *
 * @author mikeh
 * @since 2019-02-08
 */
@ConfigurationProperties(prefix = "beancounter.google")
@Service
class GoogleAuthConfig {
    private val log = LoggerFactory.getLogger(GoogleAuthConfig::class.java)

    @Value("\${api.path:../secrets/google-api/}")
    private val apiPath: String? = null

    @Value("\${api.file:credentials.json}")
    private val apiFile: String? = null

    @Value("\${api.port:8888}")
    private val port = 0
    private var receiver: LocalServerReceiver? = null

    @Bean
    fun receiver(): LocalServerReceiver? {
        log.info("Callback port {}", port)
        receiver = LocalServerReceiver.Builder()
            .setPort(port)
            .build()
        return receiver
    }

    /**
     * Authenticate against the Google Docs service. This could ask you to download a token.
     *
     * @param netHttpTransport transport
     * @return credentials
     */
    fun getCredentials(netHttpTransport: NetHttpTransport?): Credential {
        val resolved = apiPath + if (apiPath!!.endsWith("/")) apiFile else "/$apiFile"
        log.debug("Looking for credentials at {}", resolved)
        // Load client secrets.
        FileInputStream(resolved).use { `in` ->
            log.info("Reading {}", resolved)
            val clientSecrets =
                GoogleClientSecrets.load(GsonFactory.getDefaultInstance(), InputStreamReader(`in`))
            val flow = GoogleAuthorizationCodeFlow.Builder(
                netHttpTransport, GsonFactory.getDefaultInstance(), clientSecrets, SCOPES
            )
                .setAccessType("offline")
                .setDataStoreFactory(
                    FileDataStoreFactory(
                        File("$apiPath/tokens")
                    )
                )
                .setAccessType("offline")
                .build()
            return AuthorizationCodeInstalledApp(flow, receiver)
                .authorize("user")
        }
    }

    companion object {
        private val SCOPES = listOf(SheetsScopes.SPREADSHEETS_READONLY)
    }
}
