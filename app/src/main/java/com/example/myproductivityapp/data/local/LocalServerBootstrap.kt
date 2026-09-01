package com.example.myproductivityapp.data.local

import android.content.Context
import com.example.myproductivityapp.data.AppDatabase
import com.example.myproductivityapp.data.remote.RemoteDataClient
import com.example.myproductivityapp.data.repository.BottleYearRepository
import com.example.myproductivityapp.data.repository.DeliveryRecordRepository
import com.example.myproductivityapp.data.repository.EmployeeRepository
import com.example.myproductivityapp.data.repository.PriceConfigRepository

/**
 * 从旧 CloudBase 版本切换到站内服务器时执行一次。
 * 业务数据不删除，只清空旧远端 ID 并重新上传到新服务器。
 */
class LocalServerBootstrap(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("local_server_bootstrap", Context.MODE_PRIVATE)

    fun isDone(): Boolean = prefs.getBoolean("legacy_remote_migrated", false)

    suspend fun runIfNeeded(db: AppDatabase, client: RemoteDataClient): Boolean {
        if (isDone()) return true
        if (!client.health()) return false

        db.employeeDao().resetRemoteSyncForLocalServer()
        db.deliveryRecordDao().resetRemoteSyncForLocalServer()
        db.priceConfigDao().resetRemoteSyncForLocalServer()
        db.bottleYearDao().resetRemoteSyncForLocalServer()

        val employeeRepo = EmployeeRepository(db.employeeDao(), client)
        val recordRepo = DeliveryRecordRepository(db.deliveryRecordDao(), client)
        val priceRepo = PriceConfigRepository(db.priceConfigDao(), client)
        val yearRepo = BottleYearRepository(db.bottleYearDao(), client)

        employeeRepo.pushUnsynced()
        priceRepo.pushUnsynced()
        yearRepo.pushUnsynced()
        recordRepo.pushUnsynced()

        val success =
            db.employeeDao().getUnsynced().isEmpty() &&
            db.priceConfigDao().getUnsynced().isEmpty() &&
            db.bottleYearDao().getUnsynced().isEmpty() &&
            db.deliveryRecordDao().getUnsynced().isEmpty()

        if (success) {
            prefs.edit().putBoolean("legacy_remote_migrated", true).apply()
        }
        return success
    }
}
