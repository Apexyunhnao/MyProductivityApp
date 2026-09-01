package com.example.myproductivityapp.data.repository

import kotlinx.coroutines.*

class SyncManager(
    private val employeeRepo: EmployeeRepository,
    private val deliveryRecordRepo: DeliveryRecordRepository,
    private val priceConfigRepo: PriceConfigRepository,
    private val bottleYearRepo: BottleYearRepository,
    private val deliveryTaskRepo: DeliveryTaskRepository? = null
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun initialSync() {
        try {
            employeeRepo.syncFromCloud()
            deliveryRecordRepo.syncFromCloud()
            priceConfigRepo.syncFromCloud()
            bottleYearRepo.syncFromCloud()
            deliveryTaskRepo?.syncFromCloud()
            flushAll()
        } catch (_: Exception) { }
    }

    fun startWatch(): Job = scope.launch {
        while (isActive) {
            delay(15_000)
            try {
                employeeRepo.syncFromCloud()
                deliveryRecordRepo.syncFromCloud()
                priceConfigRepo.syncFromCloud()
                bottleYearRepo.syncFromCloud()
                deliveryTaskRepo?.syncFromCloud()
            } catch (_: Exception) { }
        }
    }

    fun startFlush(): Job = scope.launch {
        while (isActive) {
            delay(30_000)
            try { flushAll() } catch (_: Exception) { }
        }
    }

    suspend fun flushAll() {
        employeeRepo.pushUnsynced()
        deliveryRecordRepo.pushUnsynced()
        priceConfigRepo.pushUnsynced()
        bottleYearRepo.pushUnsynced()
        deliveryTaskRepo?.pushUnsynced()
    }

    fun destroy() { scope.cancel() }
}
