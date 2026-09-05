package com.example.myproductivityapp.data.repository

import com.example.myproductivityapp.data.dao.PriceConfigDao
import com.example.myproductivityapp.data.model.PriceConfig
import com.example.myproductivityapp.data.remote.RemoteDataClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PriceConfigRepository(
    private val dao: PriceConfigDao,
    private val client: RemoteDataClient
) {
    private val table = "price_config"

    fun observeAll(): Flow<List<PriceConfig>> = dao.getAllPrices()
    suspend fun getAllOnce(): List<PriceConfig> = dao.getAllOnce()
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
                dao.insertPrice(entity.copy(synced = true))
            } else {
                val remoteId = client.add(table, data)
                dao.insertPrice(entity.copy(firestoreId = remoteId, synced = remoteId.isNotBlank()))
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
        // 旧版修改价格不带 firestoreId 曾导致服务器出现同 bottleType 多条重复记录。
        // 拉取时按 bottleType 只保留 updatedAt 最新的一条，避免旧记录按插入顺序覆盖新价格。
        val latestByType = docs
            .filter { (it["id"] ?: it["_id"] ?: "").toString().isNotBlank() }
            .groupBy { (it["bottleType"] as? String) ?: "" }
            .mapValues { (_, items) ->
                items.maxByOrNull { (it["updatedAt"] as? Number)?.toLong() ?: 0L } ?: items.first()
            }
        for ((_, obj) in latestByType) {
            val remoteId = (obj["id"] ?: obj["_id"] ?: "").toString()
            val existing = dao.getByFirestoreId(remoteId)
            val remoteUpdatedAt = (obj["updatedAt"] as? Number)?.toLong() ?: 0
            if (existing != null && existing.updatedAt >= remoteUpdatedAt) continue

            dao.insertPrice(PriceConfig(
                bottleType = (obj["bottleType"] as? String) ?: "",
                price = (obj["price"] as? Number)?.toInt() ?: 0,
                lastUpdated = (obj["lastUpdated"] as? Number)?.toLong() ?: 0,
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
