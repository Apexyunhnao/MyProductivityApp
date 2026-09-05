package com.example.myproductivityapp.data.dao

import androidx.room.*
import com.example.myproductivityapp.data.model.PriceConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceConfigDao {
    @Query("SELECT * FROM price_config")
    fun getAllPrices(): Flow<List<PriceConfig>>

    @Query("SELECT * FROM price_config")
    suspend fun getAllOnce(): List<PriceConfig>

    @Query("SELECT * FROM price_config WHERE bottleType = :bottleType")
    suspend fun getPriceByType(bottleType: String): PriceConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrice(priceConfig: PriceConfig): Long

    @Update
    suspend fun updatePrice(priceConfig: PriceConfig)

    @Delete
    suspend fun deletePrice(priceConfig: PriceConfig)

    @Query("SELECT * FROM price_config WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getByFirestoreId(firestoreId: String): PriceConfig?

    @Query("SELECT * FROM price_config WHERE synced = 0")
    suspend fun getUnsynced(): List<PriceConfig>

    @Query("DELETE FROM price_config WHERE synced = 1 AND firestoreId != '' AND firestoreId NOT IN (:cloudIds)")
    suspend fun deleteRemoteMissing(cloudIds: List<String>)

    @Query("UPDATE price_config SET synced = 0")
    suspend fun resetRemoteSyncForLocalServer()
}
