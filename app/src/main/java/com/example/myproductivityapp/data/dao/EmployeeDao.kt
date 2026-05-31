package com.example.myproductivityapp.data.dao

import androidx.room.*
import com.example.myproductivityapp.data.model.Employee
import kotlinx.coroutines.flow.Flow

@Dao
interface EmployeeDao {
    @Query("SELECT * FROM employees ORDER BY name ASC")
    fun getAllEmployees(): Flow<List<Employee>>

    @Query("SELECT * FROM employees WHERE id = :id")
    suspend fun getEmployeeById(id: Long): Employee?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployee(employee: Employee): Long

    @Update
    suspend fun updateEmployee(employee: Employee)

    @Delete
    suspend fun deleteEmployee(employee: Employee)

    @Query("SELECT * FROM employees WHERE employeeId = :employeeId")
    suspend fun getEmployeeByEmployeeId(employeeId: String): Employee?

    @Query("SELECT * FROM employees WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getByFirestoreId(firestoreId: String): Employee?

    @Query("SELECT * FROM employees WHERE synced = 0")
    suspend fun getUnsynced(): List<Employee>
}
