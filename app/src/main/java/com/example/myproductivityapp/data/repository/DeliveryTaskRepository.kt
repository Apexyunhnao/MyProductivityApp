package com.example.myproductivityapp.data.repository

import com.example.myproductivityapp.data.dao.DeliveryTaskDao
import com.example.myproductivityapp.data.model.DeliveryTask
import com.example.myproductivityapp.data.remote.RemoteDataClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class DeliveryTaskRepository(
    private val dao: DeliveryTaskDao,
    private val client: RemoteDataClient
) {
    private val table = "delivery_tasks"

    fun observeOpenTasks(): Flow<List<DeliveryTask>> = dao.observeOpenTasks()
    fun observeOpenTasksForEmployeeRemote(employeeRemoteId: String): Flow<List<DeliveryTask>> =
        dao.observeOpenTasksForEmployeeRemote(employeeRemoteId)

    suspend fun save(task: DeliveryTask): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entity = task.copy(updatedAt = now, synced = false)
        val localId = dao.upsert(entity)
        try {
            val data = toMap(entity)
            if (entity.firestoreId.isNotBlank()) {
                client.update(table, entity.firestoreId, data)
                dao.upsert(entity.copy(id = localId, synced = true))
            } else {
                val remoteId = client.add(table, data)
                dao.upsert(entity.copy(id = localId, firestoreId = remoteId, synced = remoteId.isNotBlank()))
            }
        } catch (_: Exception) {
            // 保留本地 unsynced，后续 flush 再补传。
        }
        localId
    }

    suspend fun update(task: DeliveryTask) = withContext(Dispatchers.IO) {
        save(task)
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
                    synced = true
                )
            )
        }
    }

    suspend fun pushUnsynced() = withContext(Dispatchers.IO) {
        dao.getUnsynced().forEach { save(it) }
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
        "updatedAt" to t.updatedAt
    )

    private fun Map<String, Any?>.string(key: String, fallback: String = "") =
        (this[key] as? String) ?: fallback

    private fun Map<String, Any?>.int(key: String) =
        (this[key] as? Number)?.toInt() ?: 0

    private fun Map<String, Any?>.double(key: String) =
        (this[key] as? Number)?.toDouble() ?: 0.0
}
