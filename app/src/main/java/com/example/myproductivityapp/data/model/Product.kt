package com.example.myproductivityapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 煤气配件商品，例如减压阀、胶管、卡箍等。 */
@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val unit: String = "个",
    val defaultPrice: Double = 0.0,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val firestoreId: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)

/** 一笔配送/销售记录里卖出的配件。 */
@Entity(tableName = "product_sale_items")
data class ProductSaleItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deliveryRecordId: Long,
    val productId: Long? = null,
    val productName: String,
    val quantity: Double,
    val unit: String = "个",
    val unitPrice: Double,
    val firestoreId: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)
