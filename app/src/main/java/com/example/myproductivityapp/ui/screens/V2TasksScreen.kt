package com.example.myproductivityapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myproductivityapp.data.AppDatabase
import com.example.myproductivityapp.data.local.DeviceIdentity
import com.example.myproductivityapp.data.model.DeliveryTask
import com.example.myproductivityapp.data.model.PaymentStatus
import com.example.myproductivityapp.data.model.TaskPriority
import com.example.myproductivityapp.data.model.TaskStatus
import com.example.myproductivityapp.data.model.TaskType
import kotlinx.coroutines.launch

/**
 * V2 待办：普通待办只记客户姓名。
 * 5 人小站共享同一份未完成列表，谁处理完谁点“完成”。
 * 数据模型仍保留地址/付款/数量等字段，以兼容已有特殊任务。
 */
@Composable
fun V2TasksScreen(identity: DeviceIdentity) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()

    var tasks by remember { mutableStateOf<List<DeliveryTask>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }

    // 人少且彼此熟悉客户：所有人共享同一份未完成待办，不再要求分配负责人。
    LaunchedEffect(Unit) {
        db.deliveryTaskDao().observeOpenTasks().collect { tasks = it }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("记一个待办", fontSize = 18.sp) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text("待办", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("只记客户名字，谁送完谁点完成。", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))

            if (tasks.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "现在没有未完成任务",
                        modifier = Modifier.padding(24.dp),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(tasks, key = { it.id }) { task ->
                        TaskCard(task = task) {
                            scope.launch {
                                db.deliveryTaskDao().update(
                                    task.copy(
                                        status = TaskStatus.COMPLETED.name,
                                        completedAt = System.currentTimeMillis(),
                                        updatedAt = System.currentTimeMillis(),
                                        synced = false
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddTaskDialog(
            identity = identity,
            onDismiss = { showAddDialog = false },
            onSave = { task ->
                scope.launch {
                    db.deliveryTaskDao().upsert(task)
                    showAddDialog = false
                }
            }
        )
    }
}

@Composable
private fun TaskCard(task: DeliveryTask, onComplete: () -> Unit) {
    val simpleReminder = task.taskType == TaskType.DELIVERY.name &&
        task.address.isBlank() &&
        task.deliveryQuantity == 0 &&
        task.pickupQuantity == 0 &&
        task.assignedEmployeeName.isBlank() &&
        task.paymentStatus == PaymentStatus.UNPAID.name &&
        task.amountToCollect == 0.0 &&
        task.amountPaid == 0.0 &&
        task.debtReminder == 0.0 &&
        task.priority == TaskPriority.NORMAL.name &&
        task.note.isBlank()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                task.customerName.ifBlank { "未写客户" },
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            // 旧的特殊任务仍保留详细展示；新建的普通待办只显示客户姓名。
            if (!simpleReminder) {
                val typeText = when (task.taskType) {
                    TaskType.CUSTOMER_DROPOFF.name -> "客户已拿瓶到站"
                    TaskType.PICKUP_ONLY.name -> "只收瓶"
                    TaskType.RENTAL.name -> "租瓶"
                    TaskType.EXCHANGE.name -> "换瓶"
                    else -> "送气"
                }
                Text(typeText, fontWeight = FontWeight.Medium)

                if (task.address.isNotBlank()) Text(task.address, fontSize = 18.sp)

                if (task.deliveryQuantity > 0 || task.pickupQuantity > 0) {
                    Text(
                        buildString {
                            if (task.deliveryQuantity > 0) append("送 ${task.deliveryQuantity} 瓶")
                            if (task.deliveryQuantity > 0 && task.pickupQuantity > 0) append("　")
                            if (task.pickupQuantity > 0) append("收 ${task.pickupQuantity} 瓶")
                        },
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                val hasMoneyInfo = task.paymentStatus != PaymentStatus.UNPAID.name ||
                    task.amountToCollect > 0 || task.amountPaid > 0 || task.debtReminder > 0
                if (hasMoneyInfo) {
                    val paymentText = when (task.paymentStatus) {
                        PaymentStatus.PAID.name -> "已付款"
                        PaymentStatus.PARTIAL.name -> "部分付款"
                        PaymentStatus.DEBT.name -> "欠款"
                        else -> "未付款"
                    }
                    Text(
                        buildString {
                            append(paymentText)
                            if (task.amountPaid > 0) append(" ¥${String.format("%.0f", task.amountPaid)}")
                            if (task.debtReminder > 0) append("　旧欠 ¥${String.format("%.0f", task.debtReminder)}")
                        },
                        color = if (task.paymentStatus == PaymentStatus.PAID.name) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (task.assignedEmployeeName.isNotBlank()) Text("负责人：${task.assignedEmployeeName}")
                if (task.note.isNotBlank()) Text("备注：${task.note}")
            }

            Button(onClick = onComplete, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("完成", fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun AddTaskDialog(
    identity: DeviceIdentity,
    onDismiss: () -> Unit,
    onSave: (DeliveryTask) -> Unit
) {
    var customer by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记一个待办", fontSize = 24.sp, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = customer,
                onValueChange = { customer = it },
                label = { Text("客户姓名") },
                placeholder = { Text("例如：王叔") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 21.sp)
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        DeliveryTask(
                            customerName = customer.trim(),
                            taskType = TaskType.DELIVERY.name,
                            // 普通待办只负责“别忘了这个客户”，不强行猜数量、付款或负责人。
                            deliveryQuantity = 0,
                            pickupQuantity = 0,
                            createdByEmployeeId = identity.employeeId,
                            createdByName = identity.employeeName,
                            synced = false
                        )
                    )
                },
                enabled = customer.isNotBlank()
            ) {
                Text("保存", fontSize = 19.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
