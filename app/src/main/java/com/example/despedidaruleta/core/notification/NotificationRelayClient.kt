package com.example.despedidaruleta.core.notification

import com.example.despedidaruleta.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class NotificationRelayClient {
    suspend fun broadcast(sessionId: String, title: String, body: String, route: String? = null) = withContext(Dispatchers.IO) {
        check(BuildConfig.RELAY_BASE_URL.isNotBlank()) { "RELAY_BASE_URL no esta configurada en local.properties" }

        val payload = JSONObject()
            .put("sessionId", sessionId)
            .put("title", title)
            .put("body", body)
            .put("secret", BuildConfig.RELAY_SECRET)
        if (!route.isNullOrBlank()) payload.put("route", route)

        val url = URL("${BuildConfig.RELAY_BASE_URL}/api/notify")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }

            val status = connection.responseCode
            if (status !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error("El relay respondio $status: $errorBody")
            }
        } finally {
            connection.disconnect()
        }
    }
}
