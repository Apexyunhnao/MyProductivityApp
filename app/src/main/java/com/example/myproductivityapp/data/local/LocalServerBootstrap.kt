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
 *
 * reset 标记和 migrated 标记分开：如果上传到一半断网，重试只补传 unsynced，
 * 不会再次清空已经取得的新服务器 ID，从而避免重复创建远端记录。
 */
class LocalServerBootstrap(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("local_server_bootstrap", Context.MODE_PRIVATE)

    fun isDone(): Boolean = prefs.getBoolean(KEY_MIGRATED, false)

    suspend fun runIfNeeded(db: AppDatabase, client: RemoteDataClient): Boolean {
        if (isDone()) return true
        if (!client.health()) return false

        if (!prefs.getBoolean(KEY_RESET_DONE, false)) {
            db.employeeDao().resetRemoteSyncForLocalServer()
            db.deliveryRecordDao().resetRemoteSyncForLocalServer()
            db.priceConfigDao().resetRemoteSyncForLocalServer()
            db.bottleYearDao().resetRemoteSyncForLocalServer()
            prefs.edit().putBoolean(KEY_RESET_DONE, true).apply()
        }

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
            prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
        }
        return success
    }

    companion object {
        private const val KEY_RESET_DONE = "legacy_remote_reset_done"
        private const val KEY_MIGRATED = "legacy_remote_migrated"
    }
}
