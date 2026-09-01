package com.example.myproductivityapp.data.remote

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class LocalServerClient(
    baseUrl: String,
    private val apiKey: String
) : RemoteDataClient {
    private val base = baseUrl.trim().trimEnd('/')
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun health(): Boolean {
        return try {
            val req = authorized(Request.Builder().url("$base/api-meta/ping")).get().build()
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun list(table: String): List<Map<String, Any?>> {
        val req = authorized(Request.Builder().url("$base/api/$table")).get().build()
        val text = execute(req)
        val parsed = gson.fromJson(text, Any::class.java)
        return (parsed as? List<*>)?.mapNotNull { item ->
            (item as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }
        } ?: emptyList()
    }

    override suspend fun add(table: String, data: Map<String, Any?>): String {
        val body = gson.toJson(data).toRequestBody(JSON)
        val req = authorized(Request.Builder().url("$base/api/$table"))
            .post(body)
            .build()
        val text = execute(req)
        val map = gson.fromJson(text, Map::class.java) as? Map<*, *> ?: return ""
        return (map["id"] ?: "").toString()
    }

    override suspend fun update(table: String, docId: String, data: Map<String, Any?>) {
        val body = gson.toJson(data).toRequestBody(JSON)
        val req = authorized(Request.Builder().url("$base/api/$table/$docId"))
            .patch(body)
            .build()
        execute(req)
    }

    override suspend fun delete(table: String, docId: String) {
        val req = authorized(Request.Builder().url("$base/api/$table/$docId"))
            .delete()
            .build()
        execute(req)
    }

    private fun authorized(builder: Request.Builder): Request.Builder =
        builder.header("X-API-Key", apiKey).header("Content-Type", "application/json")

    private fun execute(req: Request): String {
        return client.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw IllegalStateException("HTTP ${res.code}: $text")
            text
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
