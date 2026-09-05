package com.example.myproductivityapp.data.repository

import com.example.myproductivityapp.data.dao.DeliveryTaskDao
import com.example.myproductivityapp.data.model.DeliveryTask
import com.example.myproductivityapp.data.remote.PhotoUtil
import com.example.myproductivityapp.data.remote.RemoteDataClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class DeliveryTaskRepository(
    private val dao: DeliveryTaskDao,
    private val client: RemoteDataClient
) {
    private val table = "delivery_tasks"

    fun observeOpenTasks(): Flow<List<DeliveryTask>> = dao.observeOpenTasks()
    fun observeOpenTasksForEmployeeRemote(employeeRemoteId: String): Flow<List<DeliveryTask>> =
        dao.observeOpenTasksForEmployeeRemote(employeeRemoteId)

    fun observeCompletedTasks(): Flow<List<DeliveryTask>> = dao.observeCompletedTasks()
    fun observeCompletedTasksForEmployeeRemote(employeeRemoteId: String): Flow<List<DeliveryTask>> =
        dao.observeCompletedTasksForEmployeeRemote(employeeRemoteId)

    suspend fun save(task: DeliveryTask): Long = withContext(Dispatchers.IO) {
        saveWithResult(task).first
    }

    /**
     * 保存并返回 (本地id, 云端是否同步成功)。
     * 本地必成功；云端失败不抛异常（保留本地 unsynced，后台 flush 补传）。
     */
    suspend fun saveWithResult(task: DeliveryTask): Pair<Long, Boolean> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entity = task.copy(updatedAt = now, synced = false)
        val localId = dao.upsert(entity)
        try {
            val data = toMap(entity)
            val ok = if (entity.firestoreId.isNotBlank()) {
                client.update(table, entity.firestoreId, data)
                dao.upsert(entity.copy(id = localId, synced = true))
                true
            } else {
                val remoteId = client.add(table, data)
                dao.upsert(entity.copy(id = localId, firestoreId = remoteId, synced = remoteId.isNotBlank()))
                remoteId.isNotBlank()
            }
            localId to ok
        } catch (_: Exception) {
            // 保留本地 unsynced，后续 flush 再补传。
            localId to false
        }
    }

    suspend fun update(task: DeliveryTask) = withContext(Dispatchers.IO) {
        save(task)
    }

    suspend fun delete(task: DeliveryTask) = withContext(Dispatchers.IO) {
        deleteWithResult(task)
    }

    /** 删除并返回云端是否同步成功。firestoreId 为空（纯本地）视为成功。 */
    suspend fun deleteWithResult(task: DeliveryTask): Boolean = withContext(Dispatchers.IO) {
        dao.deleteTask(task)
        try {
            if (task.firestoreId.isNotBlank()) {
                client.delete(table, task.firestoreId)
            }
            true
        } catch (_: Exception) { false }
    }

    suspend fun syncFromCloud() = withContext(Dispatchers.IO) {
        val docs = client.list(table)
        for (obj in docs) {
            val remoteId = (obj["id"] ?: obj["_id"] ?: "").toString()
            if (remoteId.isBlank()) continue
            val existing = dao.getByFirestoreId(remoteId)
            val remoteUpdatedAt = (obj["updatedAt"] as? Number)?.toLong() ?: 0L
            if (existing != null && existing.updatedAt >= remoteUpdatedAt) continue

            dao.upsert(
                DeliveryTask(
                    id = existing?.id ?: 0,
                    customerName = obj.string("customerName"),
                    phoneNumber = obj.string("phoneNumber"),
                    address = obj.string("address"),
                    areaTag = obj.string("areaTag"),
                    taskType = obj.string("taskType", "DELIVERY"),
                    deliveryQuantity = obj.int("deliveryQuantity"),
                    pickupQuantity = obj.int("pickupQuantity"),
                    assignedEmployeeId = (obj["assignedEmployeeId"] as? Number)?.toLong(),
                    assignedEmployeeRemoteId = obj.string("assignedEmployeeRemoteId"),
                    assignedEmployeeName = obj.string("assignedEmployeeName"),
                    paymentStatus = obj.string("paymentStatus", "UNPAID"),
                    amountToCollect = obj.double("amountToCollect"),
                    amountPaid = obj.double("amountPaid"),
                    debtReminder = obj.double("debtReminder"),
                    priority = obj.string("priority", "NORMAL"),
                    dueLabel = obj.string("dueLabel"),
                    note = obj.string("note"),
                    status = obj.string("status", "PENDING"),
                    createdByEmployeeId = (obj["createdByEmployeeId"] as? Number)?.toLong(),
                    createdByName = obj.string("createdByName"),
                    createdAt = (obj["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    completedAt = (obj["completedAt"] as? Number)?.toLong(),
                    firestoreId = remoteId,
                    updatedAt = remoteUpdatedAt,
                    synced = true,
                    bottleStatus = obj.string("bottleStatus"),
                    imagePath = obj.string("imagePath"),
                    remoteImages = obj.string("remoteImages")
                )
            )
        }
        // 删除对账：本地已同步但云端已不存在的记录 → 本地删除（他机删除同步过来）
        val cloudIds = docs.mapNotNull { (it["id"] ?: it["_id"]).toString().takeIf { id -> id.isNotBlank() } }
        dao.deleteRemoteMissing(cloudIds.ifEmpty { listOf("__none__") })
    }

    suspend fun pushUnsynced() = withContext(Dispatchers.IO) {
        dao.getUnsynced().forEach { save(it) }
    }

    /** 本机照片补传：有本地照片、尚未传服务器的待办 → 压缩上传 → 写 remoteImages 并标未同步。 */
    suspend fun pushLocalPhotos() = withContext(Dispatchers.IO) {
        for (task in dao.getPhotoPending()) {
            try {
                val bytes = PhotoUtil.compressToJpeg(File(task.imagePath)) ?: continue
                val url = client.uploadImage(bytes) ?: continue
                if (url != task.remoteImages) {
                    dao.update(task.copy(remoteImages = url, synced = false))
                }
            } catch (_: Exception) { }
        }
    }

    private fun toMap(t: DeliveryTask) = mapOf<String, Any?>(
        "customerName" to t.customerName,
        "phoneNumber" to t.phoneNumber,
        "address" to t.address,
        "areaTag" to t.areaTag,
        "taskType" to t.taskType,
        "deliveryQuantity" to t.deliveryQuantity,
        "pickupQuantity" to t.pickupQuantity,
        "assignedEmployeeId" to t.assignedEmployeeId,
        "assignedEmployeeRemoteId" to t.assignedEmployeeRemoteId,
        "assignedEmployeeName" to t.assignedEmployeeName,
        "paymentStatus" to t.paymentStatus,
        "amountToCollect" to t.amountToCollect,
        "amountPaid" to t.amountPaid,
        "debtReminder" to t.debtReminder,
        "priority" to t.priority,
        "dueLabel" to t.dueLabel,
        "note" to t.note,
        "status" to t.status,
        "createdByEmployeeId" to t.createdByEmployeeId,
        "createdByName" to t.createdByName,
        "createdAt" to t.createdAt,
        "completedAt" to t.completedAt,
        "updatedAt" to t.updatedAt,
        "bottleStatus" to t.bottleStatus,
        "imagePath" to t.imagePath,
        "remoteImages" to t.remoteImages
    )

    private fun Map<String, Any?>.string(key: String, fallback: String = "") =
        (this[key] as? String) ?: fallback

    private fun Map<String, Any?>.int(key: String) =
        (this[key] as? Number)?.toInt() ?: 0

    private fun Map<String, Any?>.double(key: String) =
        (this[key] as? Number)?.toDouble() ?: 0.0
}
