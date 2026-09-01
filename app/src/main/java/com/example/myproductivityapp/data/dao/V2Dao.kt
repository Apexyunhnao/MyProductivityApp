package com.example.myproductivityapp.data.dao

import androidx.room.*
import com.example.myproductivityapp.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DeliveryTaskDao {
    @Query("SELECT * FROM delivery_tasks WHERE status != 'COMPLETED' AND status != 'CANCELLED' ORDER BY priority DESC, createdAt ASC")
    fun observeOpenTasks(): Flow<List<DeliveryTask>>

    @Query("SELECT * FROM delivery_tasks WHERE assignedEmployeeRemoteId = :employeeRemoteId AND status != 'COMPLETED' AND status != 'CANCELLED' ORDER BY priority DESC, createdAt ASC")
    fun observeOpenTasksForEmployeeRemote(employeeRemoteId: String): Flow<List<DeliveryTask>>

    @Query("SELECT * FROM delivery_tasks WHERE assignedEmployeeId = :employeeId AND status != 'COMPLETED' AND status != 'CANCELLED' ORDER BY priority DESC, createdAt ASC")
    fun observeOpenTasksForEmployee(employeeId: Long): Flow<List<DeliveryTask>>

    @Query("SELECT * FROM delivery_tasks WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getByFirestoreId(firestoreId: String): DeliveryTask?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: DeliveryTask): Long

    @Update
    suspend fun update(task: DeliveryTask)

    @Query("SELECT * FROM delivery_tasks WHERE synced = 0")
    suspend fun getUnsynced(): List<DeliveryTask>
}

@Dao
interface BottleDetailDao {
    @Query("SELECT * FROM bottle_details WHERE deliveryRecordId = :recordId ORDER BY id")
    fun observeForRecord(recordId: Long): Flow<List<BottleDetail>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(detail: BottleDetail): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(details: List<BottleDetail>)

    @Query("SELECT * FROM bottle_details WHERE bottleCondition = :condition ORDER BY updatedAt DESC")
    fun observeByCondition(condition: String): Flow<List<BottleDetail>>
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE enabled = 1 ORDER BY sortOrder, name")
    fun observeEnabled(): Flow<List<Product>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: Product): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSaleItem(item: ProductSaleItem): Long

    @Query("SELECT * FROM product_sale_items WHERE deliveryRecordId = :recordId ORDER BY id")
    fun observeSaleItems(recordId: Long): Flow<List<ProductSaleItem>>
}

@Dao
interface PolicyDao {
    @Query("SELECT * FROM policies WHERE enabled = 1 ORDER BY updatedAt DESC")
    fun observeEnabled(): Flow<List<Policy>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(policy: Policy): Long

    @Update
    suspend fun update(policy: Policy)
}

@Dao
interface StationDutyDao {
    @Query("SELECT * FROM station_duties WHERE dutyDate >= :startOfDay AND dutyDate < :endOfDay ORDER BY assignedEmployeeName")
    fun observeForDay(startOfDay: Long, endOfDay: Long): Flow<List<StationDuty>>

    @Query("SELECT * FROM station_duties WHERE assignedEmployeeId = :employeeId AND dutyDate >= :startOfDay AND dutyDate < :endOfDay ORDER BY dutyDate")
    fun observeForEmployeeDay(employeeId: Long, startOfDay: Long, endOfDay: Long): Flow<List<StationDuty>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(duty: StationDuty): Long

    @Update
    suspend fun update(duty: StationDuty)
}
