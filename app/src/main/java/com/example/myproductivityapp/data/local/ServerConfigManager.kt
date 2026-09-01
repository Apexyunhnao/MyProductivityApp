package com.example.myproductivityapp.data.local

import android.content.Context

data class ServerConfig(
    val baseUrl: String,
    val apiKey: String
)

class ServerConfigManager(context: Context) {
    private val prefs = context.getSharedPreferences("server_config", Context.MODE_PRIVATE)

    fun load(): ServerConfig? {
        val url = prefs.getString("base_url", null)?.trim().orEmpty()
        val key = prefs.getString("api_key", null)?.trim().orEmpty()
        if (url.isBlank() || key.isBlank()) return null
        return ServerConfig(url, key)
    }

    fun save(config: ServerConfig) {
        prefs.edit()
            .putString("base_url", config.baseUrl.trim().trimEnd('/'))
            .putString("api_key", config.apiKey.trim())
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
