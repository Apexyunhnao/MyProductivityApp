package com.example.myproductivityapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "delivery_records")
data class DeliveryRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val employeeId: Long,
    val employeeName: String,
    val bottleType: String,
    val quantity: Int,
    val pricePerUnit: Double,
    val totalAmount: Double,
    val cashAmount: Double = 0.0,
    val wechatAmount: Double = 0.0,
    val debtAmount: Double = 0.0,
    val yearInfo: String = "",
    val date: Long,
    val notes: String = "",
    val imagePath: String = "",
    val firestoreId: String = "",
    val employeeFirestoreId: String = "",
    val imageUrl: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = true,
    val exchangeStatus: String = "NONE",
    val returnedYear: String = ""
)
