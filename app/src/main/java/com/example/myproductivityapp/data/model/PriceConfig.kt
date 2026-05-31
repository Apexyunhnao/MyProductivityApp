package com.example.myproductivityapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "price_config")
data class PriceConfig(
    @PrimaryKey
    val bottleType: String,
    val price: Int,
    val lastUpdated: Long,
    val firestoreId: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = true
)
