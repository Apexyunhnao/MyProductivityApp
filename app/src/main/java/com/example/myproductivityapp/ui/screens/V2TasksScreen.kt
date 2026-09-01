package com.example.myproductivityapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.example.myproductivityapp.data.local.DeviceRole
import com.example.myproductivityapp.data.model.DeliveryTask
import com.example.myproductivityapp.data.model.Employee
import com.example.myproductivityapp.data.model.PaymentStatus
import com.example.myproductivityapp.data.model.TaskPriority
import com.example.myproductivityapp.data.model.TaskStatus
import com.example.myproductivityapp.data.model.TaskType
import kotlinx.coroutines.launch

/**
 * V2 待办：录入极简，只记客户姓名 + 派给谁。
 * - 营业员/站长：看全站未完成待办；建待办必须选一个送气员。
 * - 送气员：只看派给自己的未完成待办；自己记的自动归自己。
 * 底层字段保留（兼容旧特殊任务），新建普通待办只写默认值。
 */
@Composable
fun V2TasksScreen(identity: DeviceIdentity) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()

    var tasks by remember { mutableStateOf<List<DeliveryTask>>(emptyList()) }
    var employees by remember { mutableStateOf<List<Employee>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }

    val isDriver = identity.role == DeviceRole.DRIVER

    LaunchedEffect(identity) {
        val remoteId = identity.employeeRemoteId
        if (isDriver && remoteId.isNotBlank()) {
            // 送气员只看自己的未完成待办（服务器稳定 ID，跨手机不串人）
            db.deliveryTaskDao().observeOpenTasksForEmployeeRemote(remoteId).collect { tasks = it }
        } else if (isDriver) {
            // 兼容旧绑定：换身份重新绑定一次后会使用稳定 remote id
            val localId = identity.employeeId
            if (localId != null) {
                db.deliveryTaskDao().observeOpenTasksForEmployee(localId).collect { tasks = it }
            }
        } else {
            // 营业员/站长看全站
            db.deliveryTaskDao().observeOpenTasks().collect { tasks = it }
        }
    }
    LaunchedEffect(Unit) {
        db.employeeDao().getAllEmployees().collect { employees = it }
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
            Text(
                if (isDriver) "${identity.employeeName}要做的事情"
                else "全站还没做完的事情",
                style = MaterialTheme.typography.bodyLarge
            )
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
                        TaskCard(task = task, isDriver = isDriver) {
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
            employees = employees,
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
private fun TaskCard(task: DeliveryTask, isDriver: Boolean, onComplete: () -> Unit) {
    // 新建的普通待办只带默认值，极简显示；旧的特殊任务仍展示详情，避免信息丢失。
    val simpleReminder = task.taskType == TaskType.DELIVERY.name &&
        task.address.isBlank() &&
        task.deliveryQuantity == 0 &&
        task.pickupQuantity == 0 &&
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

            // 营业员/站长端显示负责人；送气员端不用显示（一定是他自己）
            if (!isDriver && task.assignedEmployeeName.isNotBlank()) {
                Text("负责人：${task.assignedEmployeeName}", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }

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
    employees: List<Employee>,
    onDismiss: () -> Unit,
    onSave: (DeliveryTask) -> Unit
) {
    var customer by remember { mutableStateOf("") }
    val isDriver = identity.role == DeviceRole.DRIVER

    // 送气员自动选中自己；营业员/站长初始不选，必须手动挑一个送气员。
    var selectedEmployee by remember(identity, employees) {
        mutableStateOf(
            if (isDriver) {
                employees.firstOrNull { employee ->
                    if (identity.employeeRemoteId.isNotBlank()) employee.firestoreId == identity.employeeRemoteId
                    else employee.id == identity.employeeId
                }
            } else null
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记一个待办", fontSize = 24.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = customer,
                    onValueChange = { customer = it },
                    label = { Text("客户姓名") },
                    placeholder = { Text("例如：黄叔") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 21.sp)
                )

                if (!isDriver) {
                    Text("派给谁", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (employees.isEmpty()) {
                        Text(
                            "还没有员工，请先到设置里添加送气员。",
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(employees, key = { it.id }) { employee ->
                                FilterChip(
                                    selected = selectedEmployee?.id == employee.id,
                                    onClick = { selectedEmployee = employee },
                                    label = { Text(employee.name, fontSize = 17.sp) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val assignee = selectedEmployee
                    onSave(
                        DeliveryTask(
                            customerName = customer.trim(),
                            taskType = TaskType.DELIVERY.name,
                            // 普通待办不猜数量/付款/地址，全部默认值
                            deliveryQuantity = 0,
                            pickupQuantity = 0,
                            // 营业员指派 = 选中送气员；送气员自记 = 自动绑自己
                            assignedEmployeeId = assignee?.id ?: identity.employeeId.takeIf { isDriver },
                            assignedEmployeeRemoteId = assignee?.firestoreId
                                ?: identity.employeeRemoteId.takeIf { isDriver }.orEmpty(),
                            assignedEmployeeName = assignee?.name
                                ?: identity.employeeName.takeIf { isDriver }.orEmpty(),
                            createdByEmployeeId = identity.employeeId,
                            createdByName = identity.employeeName,
                            synced = false
                        )
                    )
                },
                // 营业员/站长必须选了送气员才能保存
                enabled = customer.isNotBlank() && (isDriver || selectedEmployee != null)
            ) {
                Text("保存", fontSize = 19.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
