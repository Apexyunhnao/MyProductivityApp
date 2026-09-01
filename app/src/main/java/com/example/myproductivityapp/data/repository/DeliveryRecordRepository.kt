package com.example.myproductivityapp.data.repository

import com.example.myproductivityapp.data.dao.DeliveryRecordDao
import com.example.myproductivityapp.data.model.DeliveryRecord
import com.example.myproductivityapp.data.remote.RemoteDataClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class DeliveryRecordRepository(
    private val dao: DeliveryRecordDao,
    private val client: RemoteDataClient
) {
    private val table = "delivery_records"

    fun observeAll(): Flow<List<DeliveryRecord>> = dao.getAllRecords()

    suspend fun save(record: DeliveryRecord): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entity = record.copy(updatedAt = now, synced = false)
        val localId = dao.insertRecord(entity)

        try {
            val data = recordToMap(entity)
            if (entity.firestoreId.isNotBlank()) {
                client.update(table, entity.firestoreId, data)
                dao.insertRecord(entity.copy(id = localId, synced = true))
            } else {
                val remoteId = client.add(table, data)
                dao.insertRecord(entity.copy(id = localId, firestoreId = remoteId, synced = remoteId.isNotBlank()))
            }
        } catch (e: Exception) {
            android.util.Log.e("Repo", "save failed", e)
        }
        localId
    }

    suspend fun update(record: DeliveryRecord) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entity = record.copy(updatedAt = now, synced = false)
        dao.updateRecord(entity)

        try {
            val data = recordToMap(entity)
            if (entity.firestoreId.isNotBlank()) {
                client.update(table, entity.firestoreId, data)
                dao.updateRecord(entity.copy(synced = true))
            } else {
                val remoteId = client.add(table, data)
                dao.updateRecord(entity.copy(firestoreId = remoteId, synced = remoteId.isNotBlank()))
            }
        } catch (_: Exception) { }
    }

    suspend fun delete(record: DeliveryRecord) = withContext(Dispatchers.IO) {
        dao.deleteRecord(record)
        try {
            if (record.firestoreId.isNotBlank()) client.delete(table, record.firestoreId)
        } catch (_: Exception) { }
    }

    suspend fun syncFromCloud() = withContext(Dispatchers.IO) {
        val docs = client.list(table)
        for (obj in docs) {
            val remoteId = (obj["id"] ?: obj["_id"] ?: "").toString()
            if (remoteId.isBlank()) continue
            val existing = dao.getByFirestoreId(remoteId)
            val remoteUpdatedAt = (obj["updatedAt"] as? Number)?.toLong() ?: 0
            if (existing != null && existing.updatedAt >= remoteUpdatedAt) continue

            dao.insertRecord(DeliveryRecord(
                id = existing?.id ?: 0,
                employeeId = (obj["employeeId"] as? Number)?.toLong() ?: existing?.employeeId ?: 0,
                employeeName = (obj["employeeName"] as? String) ?: "",
                bottleType = (obj["bottleType"] as? String) ?: "",
                quantity = (obj["quantity"] as? Number)?.toInt() ?: 0,
                pricePerUnit = (obj["pricePerUnit"] as? Number)?.toDouble() ?: 0.0,
                totalAmount = (obj["totalAmount"] as? Number)?.toDouble() ?: 0.0,
                cashAmount = (obj["cashAmount"] as? Number)?.toDouble() ?: 0.0,
                wechatAmount = (obj["wechatAmount"] as? Number)?.toDouble() ?: 0.0,
                debtAmount = (obj["debtAmount"] as? Number)?.toDouble() ?: 0.0,
                yearInfo = (obj["yearInfo"] as? String) ?: "",
                date = (obj["date"] as? Number)?.toLong() ?: 0,
                notes = (obj["notes"] as? String) ?: "",
                imagePath = (obj["imagePath"] as? String) ?: "",
                firestoreId = remoteId,
                employeeFirestoreId = (obj["employeeFirestoreId"] as? String) ?: "",
                imageUrl = (obj["imageUrl"] as? String) ?: "",
                updatedAt = remoteUpdatedAt,
                synced = true,
                exchangeStatus = (obj["exchangeStatus"] as? String) ?: "NONE",
                returnedYear = (obj["returnedYear"] as? String) ?: ""
            ))
        }
    }

    suspend fun pushUnsynced() {
        for (entity in dao.getUnsynced()) {
            try { save(entity) } catch (_: Exception) { }
        }
    }

    private fun recordToMap(r: DeliveryRecord) = mapOf<String, Any?>(
        "employeeId" to r.employeeId,
        "employeeName" to r.employeeName,
        "bottleType" to r.bottleType,
        "quantity" to r.quantity,
        "pricePerUnit" to r.pricePerUnit,
        "totalAmount" to r.totalAmount,
        "cashAmount" to r.cashAmount,
        "wechatAmount" to r.wechatAmount,
        "debtAmount" to r.debtAmount,
        "yearInfo" to r.yearInfo,
        "date" to r.date,
        "notes" to r.notes,
        "imagePath" to r.imagePath,
        "employeeFirestoreId" to r.employeeFirestoreId,
        "imageUrl" to r.imageUrl,
        "updatedAt" to r.updatedAt,
        "exchangeStatus" to r.exchangeStatus,
        "returnedYear" to r.returnedYear
    )
}
