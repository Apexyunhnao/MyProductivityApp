package com.example.myproductivityapp.data.cloudbase

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * CloudBase HTTP API 客户端（无需 SDK）
 */
class CloudBaseClient(val envId: String) {

    var accessToken: String = ""
        private set
    var userId: String = ""
        private set

    private val baseUrl = "https://$envId.api.tcloudbasegateway.com"

    /** 匿名登录 */
    fun signInAnonymously(): Result<String> {
        return try {
            val conn = request("POST", "/auth/v1/signin/anonymously", """{}""")
            val json = readResponse(conn)
            accessToken = json.getString("access_token")
            userId = json.optString("user_id", json.optString("sub", ""))
            Result.success(userId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 查询集合全部文档 */
    fun list(collection: String): JSONArray {
        val conn = request("GET", "/database/v1/collections/$collection/documents", null)
        val json = readResponse(conn)
        return json.optJSONArray("data") ?: JSONArray()
    }

    /** 新增文档，返回文档 ID */
    fun add(collection: String, data: Map<String, Any?>): String {
        val body = JSONObject(data.filterValues { it != null }).toString()
        val conn = request("POST", "/database/v1/collections/$collection/documents", body)
        val json = readResponse(conn)
        return json.getString("id")
    }

    /** 更新文档 */
    fun update(collection: String, docId: String, data: Map<String, Any?>) {
        val body = JSONObject(data.filterValues { it != null }).toString()
        request("PATCH", "/database/v1/collections/$collection/documents/$docId", body)
            .also { readResponse(it) }
    }

    /** 删除文档 */
    fun delete(collection: String, docId: String) {
        request("DELETE", "/database/v1/collections/$collection/documents/$docId", null)
            .also { readResponse(it) }
    }

    // ---- 内部方法 ----

    private fun request(method: String, path: String, body: String?): HttpURLConnection {
        val url = URL(baseUrl + path)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        if (accessToken.isNotBlank()) {
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
        }
        conn.doInput = true
        if (body != null) {
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream).use { it.write(body) }
        }
        return conn
    }

    private fun readResponse(conn: HttpURLConnection): JSONObject {
        val reader = BufferedReader(InputStreamReader(
            if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        ))
        val text = reader.readText()
        reader.close()
        conn.disconnect()
        return if (text.isNotBlank()) JSONObject(text) else JSONObject()
    }
}
