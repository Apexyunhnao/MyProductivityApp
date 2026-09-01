package com.example.myproductivityapp.data.dao

import androidx.room.*
import com.example.myproductivityapp.data.model.BottleYear
import kotlinx.coroutines.flow.Flow

@Dao
interface BottleYearDao {
    @Query("SELECT * FROM bottle_years ORDER BY year DESC, type ASC")
    fun getAllYears(): Flow<List<BottleYear>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertYear(year: BottleYear): Long

    @Delete
    suspend fun deleteYear(year: BottleYear)

    @Query("DELETE FROM bottle_years")
    suspend fun deleteAll()

    @Query("SELECT * FROM bottle_years WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getByFirestoreId(firestoreId: String): BottleYear?

    @Query("SELECT * FROM bottle_years WHERE synced = 0")
    suspend fun getUnsynced(): List<BottleYear>

    @Query("UPDATE bottle_years SET synced = 0")
    suspend fun resetRemoteSyncForLocalServer()
}
