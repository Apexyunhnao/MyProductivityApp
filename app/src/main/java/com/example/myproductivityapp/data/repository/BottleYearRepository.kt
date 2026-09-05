package com.example.myproductivityapp.data.repository

import com.example.myproductivityapp.data.dao.BottleYearDao
import com.example.myproductivityapp.data.model.BottleYear
import com.example.myproductivityapp.data.remote.RemoteDataClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class BottleYearRepository(
    private val dao: BottleYearDao,
    private val client: RemoteDataClient
) {
    private val table = "bottle_years"

    fun observeAll(): Flow<List<BottleYear>> = dao.getAllYears()

    suspend fun save(year: BottleYear) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entity = year.copy(updatedAt = now, synced = false)
        val localId = dao.insertYear(entity)

        try {
            val data = mapOf<String, Any?>(
                "year" to entity.year,
                "type" to entity.type,
                "updatedAt" to now
            )
            if (entity.firestoreId.isNotBlank()) {
                client.update(table, entity.firestoreId, data)
                dao.insertYear(entity.copy(id = localId, synced = true))
            } else {
                val remoteId = client.add(table, data)
                dao.insertYear(entity.copy(id = localId, firestoreId = remoteId, synced = remoteId.isNotBlank()))
            }
        } catch (_: Exception) { }
    }

    suspend fun delete(year: BottleYear) = withContext(Dispatchers.IO) {
        dao.deleteYear(year)
        try {
            if (year.firestoreId.isNotBlank()) client.delete(table, year.firestoreId)
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

            dao.insertYear(BottleYear(
                id = existing?.id ?: 0,
                year = (obj["year"] as? String) ?: "",
                type = (obj["type"] as? String) ?: "",
                firestoreId = remoteId,
                updatedAt = remoteUpdatedAt,
                synced = true
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
}
