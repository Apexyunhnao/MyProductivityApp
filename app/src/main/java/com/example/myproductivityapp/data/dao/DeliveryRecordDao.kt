package com.example.myproductivityapp.data.dao

import androidx.room.*
import com.example.myproductivityapp.data.model.DeliveryRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface DeliveryRecordDao {
    @Query("SELECT * FROM delivery_records ORDER BY date DESC")
    fun getAllRecords(): Flow<List<DeliveryRecord>>

    @Query("SELECT * FROM delivery_records WHERE employeeId = :employeeId ORDER BY date DESC")
    fun getRecordsByEmployee(employeeId: Long): Flow<List<DeliveryRecord>>

    @Query("SELECT * FROM delivery_records WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getRecordsByDateRange(startDate: Long, endDate: Long): Flow<List<DeliveryRecord>>

    @Query("SELECT * FROM delivery_records WHERE bottleType = :bottleType ORDER BY date DESC")
    fun getRecordsByBottleType(bottleType: String): Flow<List<DeliveryRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: DeliveryRecord): Long

    @Update
    suspend fun updateRecord(record: DeliveryRecord)

    @Delete
    suspend fun deleteRecord(record: DeliveryRecord)

    @Query("SELECT SUM(totalAmount) FROM delivery_records WHERE employeeId = :employeeId")
    suspend fun getTotalAmountByEmployee(employeeId: Long): Double?

    @Query("SELECT SUM(quantity) FROM delivery_records WHERE bottleType = :bottleType")
    suspend fun getTotalQuantityByType(bottleType: String): Int?

    @Query("SELECT * FROM delivery_records WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getByFirestoreId(firestoreId: String): DeliveryRecord?

    @Query("SELECT * FROM delivery_records WHERE synced = 0")
    suspend fun getUnsynced(): List<DeliveryRecord>

    @Query("SELECT * FROM delivery_records WHERE exchangeStatus = :status ORDER BY date DESC")
    fun getExchangeRecordsByStatus(status: String): Flow<List<DeliveryRecord>>

    @Query("SELECT * FROM delivery_records WHERE exchangeStatus = :status ORDER BY date DESC")
    suspend fun getExchangeRecordsByStatusOnce(status: String): List<DeliveryRecord>

    @Query("UPDATE delivery_records SET firestoreId = '', employeeFirestoreId = '', synced = 0")
    suspend fun resetRemoteSyncForLocalServer()
}
