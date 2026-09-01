package com.example.myproductivityapp.data.repository

import com.example.myproductivityapp.data.dao.EmployeeDao
import com.example.myproductivityapp.data.model.Employee
import com.example.myproductivityapp.data.remote.RemoteDataClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class EmployeeRepository(
    private val dao: EmployeeDao,
    private val client: RemoteDataClient
) {
    private val table = "employees"

    fun observeAll(): Flow<List<Employee>> = dao.getAllEmployees()

    suspend fun save(employee: Employee): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entity = employee.copy(updatedAt = now, synced = false)
        val localId = dao.insertEmployee(entity)

        try {
            val data = mapOf<String, Any?>(
                "employeeId" to entity.employeeId,
                "name" to entity.name,
                "phoneNumber" to entity.phoneNumber,
                "updatedAt" to now
            )
            if (entity.firestoreId.isNotBlank()) {
                client.update(table, entity.firestoreId, data)
                dao.insertEmployee(entity.copy(id = localId, synced = true))
            } else {
                val remoteId = client.add(table, data)
                dao.insertEmployee(entity.copy(id = localId, firestoreId = remoteId, synced = remoteId.isNotBlank()))
            }
        } catch (_: Exception) { }
        localId
    }

    suspend fun delete(employee: Employee) = withContext(Dispatchers.IO) {
        dao.deleteEmployee(employee)
        try {
            if (employee.firestoreId.isNotBlank()) client.delete(table, employee.firestoreId)
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

            dao.insertEmployee(Employee(
                id = existing?.id ?: 0,
                employeeId = (obj["employeeId"] as? String) ?: existing?.employeeId.orEmpty(),
                name = (obj["name"] as? String) ?: "",
                phoneNumber = (obj["phoneNumber"] as? String) ?: "",
                firestoreId = remoteId,
                updatedAt = remoteUpdatedAt,
                synced = true
            ))
        }
    }

    suspend fun pushUnsynced() {
        for (entity in dao.getUnsynced()) {
            try { save(entity) } catch (_: Exception) { }
        }
    }
}
