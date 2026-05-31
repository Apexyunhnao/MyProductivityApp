package com.example.myproductivityapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.myproductivityapp.data.dao.DeliveryRecordDao
import com.example.myproductivityapp.data.dao.EmployeeDao
import com.example.myproductivityapp.data.dao.PriceConfigDao
import com.example.myproductivityapp.data.dao.BottleYearDao
import com.example.myproductivityapp.data.model.DeliveryRecord
import com.example.myproductivityapp.data.model.Employee
import com.example.myproductivityapp.data.model.PriceConfig
import com.example.myproductivityapp.data.model.BottleYear

@Database(
    entities = [Employee::class, DeliveryRecord::class, PriceConfig::class, BottleYear::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun employeeDao(): EmployeeDao
    abstract fun deliveryRecordDao(): DeliveryRecordDao
    abstract fun priceConfigDao(): PriceConfigDao
    abstract fun bottleYearDao(): BottleYearDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE delivery_records ADD COLUMN cashAmount REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE delivery_records ADD COLUMN wechatAmount REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE delivery_records ADD COLUMN debtAmount REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE delivery_records ADD COLUMN yearInfo TEXT NOT NULL DEFAULT ''")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS bottle_years (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        year TEXT NOT NULL,
                        type TEXT NOT NULL
                    )
                """)
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create new price_config table with INTEGER price
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS price_config_new (
                        bottleType TEXT PRIMARY KEY NOT NULL,
                        price INTEGER NOT NULL,
                        lastUpdated INTEGER NOT NULL
                    )
                """)
                // Copy data, converting Double to Int
                database.execSQL("""
                    INSERT INTO price_config_new (bottleType, price, lastUpdated)
                    SELECT bottleType, CAST(price AS INTEGER), lastUpdated FROM price_config
                """)
                // Drop old table
                database.execSQL("DROP TABLE price_config")
                // Rename new table
                database.execSQL("ALTER TABLE price_config_new RENAME TO price_config")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE delivery_records ADD COLUMN imagePath TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // employees: 3 new columns
                database.execSQL("ALTER TABLE employees ADD COLUMN firestoreId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE employees ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE employees ADD COLUMN synced INTEGER NOT NULL DEFAULT 1")
                // delivery_records: 5 new columns
                database.execSQL("ALTER TABLE delivery_records ADD COLUMN firestoreId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE delivery_records ADD COLUMN employeeFirestoreId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE delivery_records ADD COLUMN imageUrl TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE delivery_records ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE delivery_records ADD COLUMN synced INTEGER NOT NULL DEFAULT 1")
                // price_config: 3 new columns
                database.execSQL("ALTER TABLE price_config ADD COLUMN firestoreId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE price_config ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE price_config ADD COLUMN synced INTEGER NOT NULL DEFAULT 1")
                // bottle_years: 3 new columns
                database.execSQL("ALTER TABLE bottle_years ADD COLUMN firestoreId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE bottle_years ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE bottle_years ADD COLUMN synced INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE delivery_records ADD COLUMN exchangeStatus TEXT NOT NULL DEFAULT 'NONE'")
                database.execSQL("ALTER TABLE delivery_records ADD COLUMN returnedYear TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gas_station_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
