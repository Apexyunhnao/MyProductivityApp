package com.example.myproductivityapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 待办任务：记录“还需要做什么”，与已经完成的配送记录分开。
 * assignedEmployeeRemoteId 使用服务器稳定 ID，避免不同手机的 Room 本地 id 不一致。
 */
@Entity(tableName = "delivery_tasks")
data class DeliveryTask(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val customerName: String = "",
    val phoneNumber: String = "",
    val address: String = "",
    val areaTag: String = "",
    val taskType: String = TaskType.DELIVERY.name,
    val deliveryQuantity: Int = 0,
    val pickupQuantity: Int = 0,
    val assignedEmployeeId: Long? = null,
    val assignedEmployeeRemoteId: String = "",
    val assignedEmployeeName: String = "",
    val paymentStatus: String = PaymentStatus.UNPAID.name,
    val amountToCollect: Double = 0.0,
    val amountPaid: Double = 0.0,
    val debtReminder: Double = 0.0,
    val priority: String = TaskPriority.NORMAL.name,
    val dueLabel: String = "",
    val note: String = "",
    val status: String = TaskStatus.PENDING.name,
    val createdByEmployeeId: Long? = null,
    val createdByName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val firestoreId: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false,
    // v9：瓶子归还状态（营业员/站长建待办时标记）——""=普通，"NOT_RETURNED"=瓶未回，"RETURNED"=瓶已回
    val bottleStatus: String = "",
    // v9：待办照片（本地文件路径；图片文件本身不同步，仅路径跨机可见）
    val imagePath: String = "",
    // v10：服务器照片 URL（本机原图仍在 imagePath）
    val remoteImages: String = ""
)

enum class TaskType {
    DELIVERY,
    CUSTOMER_DROPOFF,
    PICKUP_ONLY,
    RENTAL,
    EXCHANGE,
    OTHER
}

enum class TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}

enum class PaymentStatus {
    UNPAID,
    PAID,
    PARTIAL,
    DEBT
}

enum class TaskPriority {
    NORMAL,
    URGENT
}
