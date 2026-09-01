package com.example.myproductivityapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.myproductivityapp.data.dao.*
import com.example.myproductivityapp.data.model.*

@Database(
    entities = [
        Employee::class,
        DeliveryRecord::class,
        PriceConfig::class,
        BottleYear::class,
        DeliveryTask::class,
        BottleDetail::class,
        Product::class,
        ProductSaleItem::class,
        Policy::class,
        StationDuty::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun employeeDao(): EmployeeDao
    abstract fun deliveryRecordDao(): DeliveryRecordDao
    abstract fun priceConfigDao(): PriceConfigDao
    abstract fun bottleYearDao(): BottleYearDao
    abstract fun deliveryTaskDao(): DeliveryTaskDao
    abstract fun bottleDetailDao(): BottleDetailDao
    abstract fun productDao(): ProductDao
    abstract fun policyDao(): PolicyDao
    abstract fun stationDutyDao(): StationDutyDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE delivery_records ADD COLUMN cashAmount REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE delivery_records ADD COLUMN wechatAmount REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE delivery_records ADD COLUMN debtAmount REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE delivery_records ADD COLUMN yearInfo TEXT NOT NULL DEFAULT ''")
                database.execSQL("CREATE TABLE IF NOT EXISTS bottle_years (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, year TEXT NOT NULL, type TEXT NOT NULL)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS price_config_new (bottleType TEXT PRIMARY KEY NOT NULL, price INTEGER NOT NULL, lastUpdated INTEGER NOT NULL)")
                database.execSQL("INSERT INTO price_config_new (bottleType, price, lastUpdated) SELECT bottleType, CAST(price AS INTEGER), lastUpdated FROM price_config")
                database.execSQL("DROP TABLE price_config")
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
                database.execSQL("ALTER TABLE employees ADD COLUMN firestoreId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE employees ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE employees ADD COLUMN synced INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE delivery_records ADD COLUMN firestoreId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE delivery_records ADD COLUMN employeeFirestoreId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE delivery_records ADD COLUMN imageUrl TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE delivery_records ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE delivery_records ADD COLUMN synced INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE price_config ADD COLUMN firestoreId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE price_config ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE price_config ADD COLUMN synced INTEGER NOT NULL DEFAULT 1")
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

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS delivery_tasks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        customerName TEXT NOT NULL, phoneNumber TEXT NOT NULL,
                        address TEXT NOT NULL, areaTag TEXT NOT NULL,
                        taskType TEXT NOT NULL, deliveryQuantity INTEGER NOT NULL,
                        pickupQuantity INTEGER NOT NULL, assignedEmployeeId INTEGER,
                        assignedEmployeeName TEXT NOT NULL, paymentStatus TEXT NOT NULL,
                        amountToCollect REAL NOT NULL, amountPaid REAL NOT NULL,
                        debtReminder REAL NOT NULL, priority TEXT NOT NULL,
                        dueLabel TEXT NOT NULL, note TEXT NOT NULL, status TEXT NOT NULL,
                        createdByEmployeeId INTEGER, createdByName TEXT NOT NULL,
                        createdAt INTEGER NOT NULL, completedAt INTEGER,
                        firestoreId TEXT NOT NULL, updatedAt INTEGER NOT NULL,
                        synced INTEGER NOT NULL
                    )
                """)
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS bottle_details (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        deliveryRecordId INTEGER NOT NULL, bottleType TEXT NOT NULL,
                        quantity INTEGER NOT NULL, productionMark TEXT NOT NULL,
                        bottleCondition TEXT NOT NULL, customerName TEXT NOT NULL,
                        unitPrice REAL NOT NULL, policyId INTEGER, note TEXT NOT NULL,
                        firestoreId TEXT NOT NULL, updatedAt INTEGER NOT NULL,
                        synced INTEGER NOT NULL
                    )
                """)
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS products (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL, unit TEXT NOT NULL, defaultPrice REAL NOT NULL,
                        enabled INTEGER NOT NULL, sortOrder INTEGER NOT NULL,
                        firestoreId TEXT NOT NULL, updatedAt INTEGER NOT NULL,
                        synced INTEGER NOT NULL
                    )
                """)
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS product_sale_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        deliveryRecordId INTEGER NOT NULL, productId INTEGER,
                        productName TEXT NOT NULL, quantity REAL NOT NULL,
                        unit TEXT NOT NULL, unitPrice REAL NOT NULL,
                        firestoreId TEXT NOT NULL, updatedAt INTEGER NOT NULL,
                        synced INTEGER NOT NULL
                    )
                """)
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS policies (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL, policyType TEXT NOT NULL, amount REAL NOT NULL,
                        conditionText TEXT NOT NULL, startAt INTEGER, endAt INTEGER,
                        enabled INTEGER NOT NULL, firestoreId TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL, synced INTEGER NOT NULL
                    )
                """)
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS station_duties (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        dutyType TEXT NOT NULL, dutyDate INTEGER NOT NULL,
                        assignedEmployeeId INTEGER NOT NULL, assignedEmployeeName TEXT NOT NULL,
                        expectedReturnAt INTEGER, status TEXT NOT NULL,
                        swapWithEmployeeId INTEGER, note TEXT NOT NULL,
                        firestoreId TEXT NOT NULL, updatedAt INTEGER NOT NULL,
                        synced INTEGER NOT NULL
                    )
                """)
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE delivery_tasks ADD COLUMN assignedEmployeeRemoteId TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gas_station_database"
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
