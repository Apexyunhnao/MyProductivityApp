package com.example.myproductivityapp.data.repository

import com.example.myproductivityapp.data.dao.DeliveryRecordDao
import com.example.myproductivityapp.data.model.DeliveryRecord
import com.example.myproductivityapp.data.remote.PhotoUtil
import com.example.myproductivityapp.data.remote.RemoteDataClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class DeliveryRecordRepository(
    private val dao: DeliveryRecordDao,
    private val client: RemoteDataClient
) {
    private val table = "delivery_records"

    fun observeAll(): Flow<List<DeliveryRecord>> = dao.getAllRecords()

    suspend fun save(record: DeliveryRecord): Long = withContext(Dispatchers.IO) {
        saveWithResult(record).first
    }

    /**
     * 保存并返回 (本地id, 云端是否同步成功)。
     * 本地必成功；云端失败不抛异常（保留本地 unsynced，后台补传）。
     */
    suspend fun saveWithResult(record: DeliveryRecord): Pair<Long, Boolean> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entity = record.copy(updatedAt = now, synced = false)
        val localId = dao.insertRecord(entity)

        try {
            val data = recordToMap(entity)
            val ok = if (entity.firestoreId.isNotBlank()) {
                client.update(table, entity.firestoreId, data)
                dao.insertRecord(entity.copy(id = localId, synced = true))
                true
            } else {
                val remoteId = client.add(table, data)
                dao.insertRecord(entity.copy(id = localId, firestoreId = remoteId, synced = remoteId.isNotBlank()))
                remoteId.isNotBlank()
            }
            localId to ok
        } catch (e: Exception) {
            android.util.Log.e("Repo", "save failed", e)
            localId to false
        }
    }

    suspend fun update(record: DeliveryRecord) = withContext(Dispatchers.IO) {
        updateWithResult(record)
    }

    /** 更新并返回云端是否同步成功。 */
    suspend fun updateWithResult(record: DeliveryRecord): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entity = record.copy(updatedAt = now, synced = false)
        dao.updateRecord(entity)

        try {
            val data = recordToMap(entity)
            val ok = if (entity.firestoreId.isNotBlank()) {
                client.update(table, entity.firestoreId, data)
                dao.updateRecord(entity.copy(synced = true))
                true
            } else {
                val remoteId = client.add(table, data)
                dao.updateRecord(entity.copy(firestoreId = remoteId, synced = remoteId.isNotBlank()))
                remoteId.isNotBlank()
            }
            ok
        } catch (_: Exception) { false }
    }

    suspend fun delete(record: DeliveryRecord) = withContext(Dispatchers.IO) {
        deleteWithResult(record)
    }

    /** 删除并返回云端是否同步成功。firestoreId 为空（纯本地）视为成功。 */
    suspend fun deleteWithResult(record: DeliveryRecord): Boolean = withContext(Dispatchers.IO) {
        dao.deleteRecord(record)
        try {
            if (record.firestoreId.isNotBlank()) {
                client.delete(table, record.firestoreId)
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
                remoteImages = (obj["remoteImages"] as? String) ?: "",
                updatedAt = remoteUpdatedAt,
                synced = true,
                exchangeStatus = (obj["exchangeStatus"] as? String) ?: "NONE",
                returnedYear = (obj["returnedYear"] as? String) ?: ""
            ))
        }
        // 删除对账：本地已同步但云端已不存在的记录 → 本地删除（他机删除同步过来）
        val cloudIds = docs.mapNotNull { (it["id"] ?: it["_id"]).toString().takeIf { id -> id.isNotBlank() } }
        dao.deleteRemoteMissing(cloudIds.ifEmpty { listOf("__none__") })
    }

    suspend fun pushUnsynced() {
        for (entity in dao.getUnsynced()) {
            try { save(entity) } catch (_: Exception) { }
        }
    }

    /**
     * 本机照片补传：有本地照片、尚未传服务器的记录 → 压缩上传 → 写 remoteImages 并标未同步，
     * 下次 pushUnsynced 把带照片 URL 的记录推给服务器，其他手机即可看到。
     * 顺序 = 押金单(imagePath) + 现场单据(imageUrl 本地路径)；断网时自动留待下轮。
     */
    suspend fun pushLocalPhotos() = withContext(Dispatchers.IO) {
        for (record in dao.getPhotoPending()) {
            try {
                val localPaths = listOfNotNull(
                    record.imagePath.takeIf { it.isNotBlank() },
                    record.imageUrl.takeIf { it.isNotBlank() && !it.startsWith("http") }
                )
                val urls = mutableListOf<String>()
                for (p in localPaths) {
                    val bytes = PhotoUtil.compressToJpeg(File(p)) ?: continue
                    val url = client.uploadImage(bytes) ?: continue
                    urls.add(url)
                }
                val joined = PhotoUtil.joinRemoteUrls(urls)
                if (joined.isNotBlank() && joined != record.remoteImages) {
                    dao.updateRecord(record.copy(remoteImages = joined, synced = false))
                }
            } catch (_: Exception) { }
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
        "remoteImages" to r.remoteImages,
        "updatedAt" to r.updatedAt,
        "exchangeStatus" to r.exchangeStatus,
        "returnedYear" to r.returnedYear
    )
}
