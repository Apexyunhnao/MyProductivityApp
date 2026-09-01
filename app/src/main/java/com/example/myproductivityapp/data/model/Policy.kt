package com.example.myproductivityapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 公司临时政策/活动。比如“旧瓶补70元换新”。
 * 金额、有效期、适用条件都配置化，避免写死在 App 代码里。
 */
@Entity(tableName = "policies")
data class Policy(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val policyType: String = PolicyType.BOTTLE_REPLACEMENT.name,
    val amount: Double = 0.0,
    val conditionText: String = "",
    val startAt: Long? = null,
    val endAt: Long? = null,
    val enabled: Boolean = true,
    val firestoreId: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)

enum class PolicyType {
    BOTTLE_REPLACEMENT,
    DISCOUNT,
    OTHER
}
