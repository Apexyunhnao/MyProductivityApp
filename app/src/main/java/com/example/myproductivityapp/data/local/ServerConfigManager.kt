package com.example.myproductivityapp.data.local

import android.content.Context

data class ServerConfig(
    val baseUrl: String,
    val apiKey: String
)

class ServerConfigManager(context: Context) {
    private val prefs = context.getSharedPreferences("server_config", Context.MODE_PRIVATE)

    fun load(): ServerConfig? {
        // 用户主动清除过 → 纯本地模式（不自动恢复默认）
        if (prefs.getBoolean(KEY_USER_DISABLED, false)) return null
        val url = prefs.getString("base_url", null)?.trim().orEmpty()
        val key = prefs.getString("api_key", null)?.trim().orEmpty()
        // 从未配置过，或还停留在旧默认服务器（新加坡试用）→ 用内置默认服务器（阿里云正式服务器）
        if (url.isBlank() || key.isBlank() || url == OLD_DEFAULT_BASE_URL) {
            return ServerConfig(DEFAULT_BASE_URL, DEFAULT_API_KEY)
        }
        return ServerConfig(url, key)
    }

    fun save(config: ServerConfig) {
        prefs.edit()
            .putString("base_url", config.baseUrl.trim().trimEnd('/'))
            .putString("api_key", config.apiKey.trim())
            .putBoolean(KEY_USER_DISABLED, false)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().putBoolean(KEY_USER_DISABLED, true).apply()
    }

    companion object {
        // 默认服务器：阿里云 ECS 120.25.221.71（正式，2026-09-05 起）
        const val DEFAULT_BASE_URL = "http://120.25.221.71:8000"
        const val DEFAULT_API_KEY = "gas-station-local"
        // 旧默认服务器（新加坡试用 43.134.58.30）——手机里残留旧地址时自动切到新默认
        private const val OLD_DEFAULT_BASE_URL = "http://43.134.58.30:8000"
        private const val KEY_USER_DISABLED = "user_disabled"
    }
}
