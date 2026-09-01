package com.example.myproductivityapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一笔配送记录中的瓶子明细。
 * 不再把租瓶对象、厂/检年份、换瓶等关键业务数据拼进 notes 字符串。
 */
@Entity(tableName = "bottle_details")
data class BottleDetail(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deliveryRecordId: Long,
    val bottleType: String,
    val quantity: Int,
    val productionMark: String = "",
    val bottleCondition: String = BottleCondition.NORMAL.name,
    val customerName: String = "",
    val unitPrice: Double = 0.0,
    val policyId: Long? = null,
    val note: String = "",
    val firestoreId: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)

enum class BottleCondition {
    NORMAL,
    NEED_INSPECTION,
    INSPECTION_PENDING,
    SCRAPPED,
    REPLACED
}
