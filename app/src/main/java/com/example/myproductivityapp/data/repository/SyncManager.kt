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
        // 先补传本机照片（写回 remoteImages + 标未同步），再推记录/待办——这样一轮 flush 照片 URL 就能上服务器
        deliveryRecordRepo.pushLocalPhotos()
        deliveryTaskRepo?.pushLocalPhotos()
        employeeRepo.pushUnsynced()
        deliveryRecordRepo.pushUnsynced()
        priceConfigRepo.pushUnsynced()
        bottleYearRepo.pushUnsynced()
        deliveryTaskRepo?.pushUnsynced()
    }

    fun destroy() { scope.cancel() }
}
