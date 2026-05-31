package com.example.myproductivityapp.data.repository

import com.example.myproductivityapp.data.cloudbase.CloudBaseClient
import com.example.myproductivityapp.data.dao.PriceConfigDao
import com.example.myproductivityapp.data.model.PriceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PriceConfigRepository(
    private val dao: PriceConfigDao,
    private val client: CloudBaseClient
) {
    private val table = "price_config"

    fun observeAll(): Flow<List<PriceConfig>> = dao.getAllPrices()
    suspend fun getByType(bottleType: String): PriceConfig? = dao.getPriceByType(bottleType)

    suspend fun save(config: PriceConfig) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entity = config.copy(lastUpdated = now, updatedAt = now, synced = false)
        dao.insertPrice(entity)

        try {
            val data = mapOf<String, Any?>(
                "bottleType" to entity.bottleType,
                "price" to entity.price,
                "lastUpdated" to entity.lastUpdated,
                "updatedAt" to now
            )
            if (entity.firestoreId.isNotBlank()) {
                client.update(table, entity.firestoreId, data)
            } else {
                val fsId = client.add(table, data)
                dao.insertPrice(entity.copy(firestoreId = fsId, synced = true))
            }
        } catch (_: Exception) { }
    }

    suspend fun delete(config: PriceConfig) = withContext(Dispatchers.IO) {
        dao.deletePrice(config)
        try {
            if (config.firestoreId.isNotBlank()) client.delete(table, config.firestoreId)
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

            dao.insertPrice(PriceConfig(
                bottleType = (obj["bottleType"] as? String) ?: "",
                price = (obj["price"] as? Number)?.toInt() ?: 0,
                lastUpdated = (obj["lastUpdated"] as? Number)?.toLong() ?: 0,
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
