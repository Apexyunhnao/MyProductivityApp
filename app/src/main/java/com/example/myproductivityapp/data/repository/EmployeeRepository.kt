package com.example.myproductivityapp.data.repository

import com.example.myproductivityapp.data.cloudbase.CloudBaseClient
import com.example.myproductivityapp.data.dao.EmployeeDao
import com.example.myproductivityapp.data.model.Employee
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject

class EmployeeRepository(
    private val dao: EmployeeDao,
    private val client: CloudBaseClient
) {
    private val table = "employees"

    fun observeAll(): Flow<List<Employee>> = dao.getAllEmployees()

    suspend fun save(employee: Employee): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entity = employee.copy(updatedAt = now, synced = false)
        val localId = dao.insertEmployee(entity)

        try {
            val data = mapOf<String, Any?>(
                "name" to entity.name,
                "phoneNumber" to entity.phoneNumber,
                "updatedAt" to now
            )
            if (entity.firestoreId.isNotBlank()) {
                client.update(table, entity.firestoreId, data)
            } else {
                val fsId = client.add(table, data)
                dao.insertEmployee(entity.copy(id = localId, firestoreId = fsId, synced = true))
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
            val fsId = (obj["id"] ?: obj["_id"] ?: "").toString()
            if (fsId.isBlank()) continue
            val existing = dao.getByFirestoreId(fsId)
            val remoteUpdatedAt = (obj["updatedAt"] as? Number)?.toLong() ?: 0
            if (existing != null && existing.updatedAt >= remoteUpdatedAt) continue

            dao.insertEmployee(Employee(
                id = existing?.id ?: 0,
                employeeId = existing?.employeeId ?: "",
                name = (obj["name"] as? String) ?: "",
                phoneNumber = (obj["phoneNumber"] as? String) ?: "",
                firestoreId = fsId,
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
