package com.example.myproductivityapp.data.repository

import com.example.myproductivityapp.data.cloudbase.CloudBaseClient
import com.example.myproductivityapp.data.dao.BottleYearDao
import com.example.myproductivityapp.data.model.BottleYear
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class BottleYearRepository(
    private val dao: BottleYearDao,
    private val client: CloudBaseClient
) {
    private val table = "bottle_years"

    fun observeAll(): Flow<List<BottleYear>> = dao.getAllYears()

    suspend fun save(year: BottleYear) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entity = year.copy(updatedAt = now, synced = false)
        val localId = dao.insertYear(entity.copy(synced = false))

        try {
            val data = mapOf<String, Any?>("year" to entity.year, "type" to entity.type, "updatedAt" to now)
            if (entity.firestoreId.isNotBlank()) {
                client.update(table, entity.firestoreId, data)
            } else {
                val fsId = client.add(table, data)
                dao.insertYear(entity.copy(id = localId, firestoreId = fsId, synced = true))
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
            val fsId = (obj["id"] ?: obj["_id"] ?: "").toString()
            if (fsId.isBlank()) continue
            val existing = dao.getByFirestoreId(fsId)
            val remoteUpdatedAt = (obj["updatedAt"] as? Number)?.toLong() ?: 0
            if (existing != null && existing.updatedAt >= remoteUpdatedAt) continue

            dao.insertYear(BottleYear(
                id = existing?.id ?: 0,
                year = (obj["year"] as? String) ?: "",
                type = (obj["type"] as? String) ?: "",
                firestoreId = fsId, updatedAt = remoteUpdatedAt, synced = true
            ))
        }
    }

    suspend fun pushUnsynced() {
        for (entity in dao.getUnsynced()) {
            try { save(entity) } catch (_: Exception) { }
        }
    }
}
