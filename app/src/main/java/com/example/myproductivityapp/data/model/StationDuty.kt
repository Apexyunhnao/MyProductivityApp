package com.example.myproductivityapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 站内公共任务。目前只用于卸瓶轮值/回站提醒。
 * 不包含洗瓶，避免把 App 扩成考勤或监控工具。
 */
@Entity(tableName = "station_duties")
data class StationDuty(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dutyType: String = StationDutyType.UNLOADING.name,
    val dutyDate: Long,
    val assignedEmployeeId: Long,
    val assignedEmployeeName: String,
    val expectedReturnAt: Long? = null,
    val status: String = StationDutyStatus.ASSIGNED.name,
    val swapWithEmployeeId: Long? = null,
    val note: String = "",
    val firestoreId: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)

enum class StationDutyType {
    UNLOADING
}

enum class StationDutyStatus {
    ASSIGNED,
    ACKNOWLEDGED,
    SWAPPED,
    COMPLETED,
    MISSED,
    CANCELLED
}
