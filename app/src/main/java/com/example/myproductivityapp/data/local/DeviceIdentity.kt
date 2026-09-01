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
    val employeeName: String = ""
)

/**
 * V2 不做账号密码。第一次选择“这台手机是谁”，之后存在本机。
 */
class DeviceIdentityManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("v2_device_identity", Context.MODE_PRIVATE)

    fun load(): DeviceIdentity? {
        val roleName = prefs.getString("role", null) ?: return null
        val role = runCatching { DeviceRole.valueOf(roleName) }.getOrNull() ?: return null
        val employeeId = prefs.getLong("employee_id", -1L).takeIf { it > 0L }
        val employeeName = prefs.getString("employee_name", "").orEmpty()
        return DeviceIdentity(role, employeeId, employeeName)
    }

    fun save(identity: DeviceIdentity) {
        prefs.edit()
            .putString("role", identity.role.name)
            .putLong("employee_id", identity.employeeId ?: -1L)
            .putString("employee_name", identity.employeeName)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
