package com.example.myproductivityapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bottle_years")
data class BottleYear(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val year: String,
    val type: String,
    val firestoreId: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = true
)
