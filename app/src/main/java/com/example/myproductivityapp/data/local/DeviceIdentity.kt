package com.example.myproductivityapp.data.local

import android.content.Context

enum class DeviceRole {
    OFFICE,
    DRIVER,
    MANAGER
}

data class DeviceIdentity(
    val role: DeviceRole,
    val employeeId: Long? = null,
    val employeeRemoteId: String = "",
    val employeeName: String = "",
    val phone: String = ""
)

/**
 * V2 不做账号密码。第一次选择“这台手机是谁”，之后存在本机。
 * employeeRemoteId 使用服务器 UUID，跨手机稳定。
 */
class DeviceIdentityManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("v2_device_identity", Context.MODE_PRIVATE)

    fun load(): DeviceIdentity? {
        val roleName = prefs.getString("role", null) ?: return null
        val role = runCatching { DeviceRole.valueOf(roleName) }.getOrNull() ?: return null
        val employeeId = prefs.getLong("employee_id", -1L).takeIf { it > 0L }
        val employeeRemoteId = prefs.getString("employee_remote_id", "").orEmpty()
        val employeeName = prefs.getString("employee_name", "").orEmpty()
        val phone = prefs.getString("phone", "").orEmpty()
        return DeviceIdentity(role, employeeId, employeeRemoteId, employeeName, phone)
    }

    fun save(identity: DeviceIdentity) {
        prefs.edit()
            .putString("role", identity.role.name)
            .putLong("employee_id", identity.employeeId ?: -1L)
            .putString("employee_remote_id", identity.employeeRemoteId)
            .putString("employee_name", identity.employeeName)
            .putString("phone", identity.phone)
            .putBoolean("identity_configured_before", true)
            .apply()
    }

    fun clear() {
        // 只清身份字段，保留 identity_configured_before 标记（换身份时仍要确认）
        prefs.edit()
            .remove("role")
            .remove("employee_id")
            .remove("employee_remote_id")
            .remove("employee_name")
            .remove("phone")
            .apply()
    }

    /** 这台手机是否曾经配置过身份（首次设置=false，之后换身份=true） */
    fun hasIdentityBefore(): Boolean = prefs.getBoolean("identity_configured_before", false)
}
