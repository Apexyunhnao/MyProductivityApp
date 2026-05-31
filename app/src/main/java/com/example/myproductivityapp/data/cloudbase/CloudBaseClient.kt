package com.example.myproductivityapp.data.cloudbase

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class CloudBaseClient(val envId: String) {
    var accessToken: String = ""
        private set
    var userId: String = ""
        private set

    private val authUrl = "https://$envId.api.tcloudbasegateway.com"
    private val dbUrl = "https://$envId.api.tcloudbasegateway.com/v1/rdb/rest"
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun signInAnonymously(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = """{}""".toRequestBody(JSON)
            val req = Request.Builder().url("$authUrl/auth/v1/signin/anonymously")
                .header("Content-Type", "application/json")
                .header("X-Device-Id", deviceId)
                .post(body).build()
            val res = client.newCall(req).execute()
            val text = res.body?.string() ?: ""
            val map = gson.fromJson(text, Map::class.java) as? Map<*, *> ?: emptyMap<String, Any>()

            val token = (map["access_token"] ?: map["token"] ?: "").toString()
            val uid = (map["user_id"] ?: map["uid"] ?: "").toString()

            if (token.isBlank()) Result.failure(Exception("登录返回: $text"))
            else {
                accessToken = token; userId = uid; Result.success(uid)
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun list(table: String): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$dbUrl/$table")
            .header("Authorization", "Bearer $accessToken").get().build()
        executeList(req)
    }

    suspend fun add(table: String, data: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        val body = gson.toJson(data).toRequestBody(JSON)
        val req = Request.Builder().url("$dbUrl/$table")
            .header("Authorization", "Bearer $accessToken")
            .header("Prefer", "return=representation").post(body).build()
        val list = executeList(req)
        (list.firstOrNull()?.get("id") ?: "").toString()
    }

    suspend fun update(table: String, docId: String, data: Map<String, Any?>) = withContext(Dispatchers.IO) {
        val body = gson.toJson(data).toRequestBody(JSON)
        val req = Request.Builder().url("$dbUrl/$table?id=eq.$docId")
            .header("Authorization", "Bearer $accessToken").patch(body).build()
        client.newCall(req).execute().close()
    }

    suspend fun delete(table: String, docId: String) = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$dbUrl/$table?id=eq.$docId")
            .header("Authorization", "Bearer $accessToken").delete().build()
        client.newCall(req).execute().close()
    }

    private suspend fun executeList(req: Request): List<Map<String, Any?>> {
        val text = client.newCall(req).execute().body?.string() ?: ""
        val result = gson.fromJson(text, Any::class.java)
        return when (result) {
            is List<*> -> result.map { (it as? Map<*, *> ?: emptyMap<String, Any?>()).mapKeys { k -> k.key.toString() } }
            is Map<*, *> -> listOf(result.mapKeys { it.key.toString() })
            else -> emptyList()
        } as? List<Map<String, Any?>> ?: emptyList()
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val deviceId = java.util.UUID.randomUUID().toString()
    }
}
