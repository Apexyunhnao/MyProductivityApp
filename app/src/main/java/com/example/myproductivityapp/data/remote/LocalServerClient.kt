package com.example.myproductivityapp.data.remote

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    override suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = authorized(Request.Builder().url("$base/api-meta/ping")).get().build()
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun list(table: String): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val req = authorized(Request.Builder().url("$base/api/$table")).get().build()
        val text = execute(req)
        val parsed = gson.fromJson(text, Any::class.java)
        (parsed as? List<*>)?.mapNotNull { item ->
            (item as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }
        } ?: emptyList()
    }

    override suspend fun add(table: String, data: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        val body = gson.toJson(data).toRequestBody(JSON)
        val req = authorized(Request.Builder().url("$base/api/$table"))
            .post(body)
            .build()
        val text = execute(req)
        val map = gson.fromJson(text, Map::class.java) as? Map<*, *> ?: return@withContext ""
        (map["id"] ?: "").toString()
    }

    override suspend fun update(table: String, docId: String, data: Map<String, Any?>) = withContext(Dispatchers.IO) {
        val body = gson.toJson(data).toRequestBody(JSON)
        val req = authorized(Request.Builder().url("$base/api/$table/$docId"))
            .patch(body)
            .build()
        execute(req)
        Unit
    }

    override suspend fun delete(table: String, docId: String) = withContext(Dispatchers.IO) {
        val req = authorized(Request.Builder().url("$base/api/$table/$docId"))
            .delete()
            .build()
        execute(req)
        Unit
    }

    /** 上传压缩后照片（jpg 字节）到服务器，返回完整可访问 URL；失败返回 null。 */
    override suspend fun uploadImage(bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        try {
            val body = bytes.toRequestBody("image/jpeg".toMediaType())
            val req = Request.Builder()
                .url("$base/api-meta/upload_image")
                .header("X-API-Key", apiKey)
                .post(body)
                .build()
            val text = client.newCall(req).execute().use { res ->
                val t = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw IllegalStateException("HTTP ${res.code}: $t")
                t
            }
            val map = gson.fromJson(text, Map::class.java) as? Map<*, *> ?: return@withContext null
            (map["url"] ?: "").toString().takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
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
