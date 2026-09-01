package com.example.myproductivityapp.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.example.myproductivityapp.data.model.DeliveryRecord

/**
 * 旧 StatisticsScreen 遗留的“归还换瓶”入口兼容层。
 * V2 默认不再进入旧统计页；先保留最小实现，避免旧源码阻塞编译。
 */
var recordToReturn: DeliveryRecord? = null

@Composable
fun ReturnDialog(
    record: DeliveryRecord,
    onDismiss: () -> Unit,
    onConfirm: (DeliveryRecord) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认归还") },
        text = { Text("确认这条换瓶记录已经处理完成？") },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(record.copy(exchangeStatus = "RETURNED"))
                }
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
