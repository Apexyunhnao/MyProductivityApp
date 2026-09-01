package com.example.myproductivityapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myproductivityapp.data.AppDatabase
import com.example.myproductivityapp.data.local.DeviceIdentity
import com.example.myproductivityapp.data.local.DeviceRole
import com.example.myproductivityapp.data.model.*
import kotlinx.coroutines.launch

@Composable
fun V2TasksScreen(identity: DeviceIdentity) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()

    var tasks by remember { mutableStateOf<List<DeliveryTask>>(emptyList()) }
    var employees by remember { mutableStateOf<List<Employee>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(identity) {
        val remoteId = identity.employeeRemoteId
        if (identity.role == DeviceRole.DRIVER && remoteId.isNotBlank()) {
            db.deliveryTaskDao().observeOpenTasksForEmployeeRemote(remoteId).collect { tasks = it }
        } else if (identity.role == DeviceRole.DRIVER) {
            // 兼容旧绑定；重新“换身份”绑定一次后会使用稳定 remote id。
            val localId = identity.employeeId
            if (localId != null) {
                db.deliveryTaskDao().observeOpenTasksForEmployee(localId).collect { tasks = it }
            }
        } else {
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
                if (identity.role == DeviceRole.DRIVER) "${identity.employeeName}要做的事情"
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
            employees = employees,
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
    val paymentText = when (task.paymentStatus) {
        PaymentStatus.PAID.name -> "已付款"
        PaymentStatus.PARTIAL.name -> "部分付款"
        PaymentStatus.DEBT.name -> "欠款"
        else -> "未付款"
    }
    val typeText = when (task.taskType) {
        TaskType.CUSTOMER_DROPOFF.name -> "客户已拿瓶到站"
        TaskType.PICKUP_ONLY.name -> "只收瓶"
        TaskType.RENTAL.name -> "租瓶"
        TaskType.EXCHANGE.name -> "换瓶"
        else -> "送气"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(task.customerName.ifBlank { "未写客户" }, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (task.priority == TaskPriority.URGENT.name) {
                    AssistChip(onClick = {}, label = { Text("急") })
                }
            }
            if (task.address.isNotBlank()) Text(task.address, fontSize = 18.sp)
            Text(typeText, fontWeight = FontWeight.Medium)

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

            Text(
                buildString {
                    append(paymentText)
                    if (task.amountPaid > 0) append(" ¥${String.format("%.0f", task.amountPaid)}")
                    if (task.debtReminder > 0) append("　旧欠 ¥${String.format("%.0f", task.debtReminder)}")
                },
                color = if (task.paymentStatus == PaymentStatus.PAID.name) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            if (task.assignedEmployeeName.isNotBlank()) Text("负责人：${task.assignedEmployeeName}")
            if (task.note.isNotBlank()) Text("备注：${task.note}")

            Button(onClick = onComplete, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("完成", fontSize = 19.sp)
            }
        }
    }
}

@Composable
private fun AddTaskDialog(
    employees: List<Employee>,
    identity: DeviceIdentity,
    onDismiss: () -> Unit,
    onSave: (DeliveryTask) -> Unit
) {
    var type by remember { mutableStateOf(TaskType.DELIVERY) }
    var customer by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var deliveryQty by remember { mutableStateOf("1") }
    var pickupQty by remember { mutableStateOf("1") }
    var payment by remember { mutableStateOf(PaymentStatus.UNPAID) }
    var amountPaid by remember { mutableStateOf("") }
    var oldDebt by remember { mutableStateOf("") }
    var selectedEmployee by remember(identity, employees) {
        mutableStateOf(
            if (identity.role == DeviceRole.DRIVER) {
                employees.firstOrNull { employee ->
                    if (identity.employeeRemoteId.isNotBlank()) employee.firestoreId == identity.employeeRemoteId
                    else employee.id == identity.employeeId
                }
            } else null
        )
    }
    var urgent by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记一个待办", fontSize = 24.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(type == TaskType.DELIVERY, { type = TaskType.DELIVERY }, { Text("送气") }) }
                    item { FilterChip(type == TaskType.CUSTOMER_DROPOFF, { type = TaskType.CUSTOMER_DROPOFF }, { Text("客户拿瓶来") }) }
                    item { FilterChip(type == TaskType.PICKUP_ONLY, { type = TaskType.PICKUP_ONLY }, { Text("只收瓶") }) }
                }

                OutlinedTextField(customer, { customer = it }, label = { Text("客户/称呼") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(address, { address = it }, label = { Text("村 / 地址") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                if (type != TaskType.PICKUP_ONLY) {
                    NumberField(
                        if (type == TaskType.CUSTOMER_DROPOFF) "以后要送回几瓶" else "要送几瓶",
                        deliveryQty
                    ) { deliveryQty = it }
                }
                NumberField(
                    if (type == TaskType.CUSTOMER_DROPOFF) "客户拿来几瓶" else "要收几瓶",
                    pickupQty
                ) { pickupQty = it }

                Text("钱", fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(payment == PaymentStatus.UNPAID, { payment = PaymentStatus.UNPAID }, { Text("未付款") }) }
                    item { FilterChip(payment == PaymentStatus.PAID, { payment = PaymentStatus.PAID }, { Text("已付款") }) }
                    item { FilterChip(payment == PaymentStatus.DEBT, { payment = PaymentStatus.DEBT }, { Text("欠款") }) }
                }
                if (payment == PaymentStatus.PAID) {
                    MoneyTaskField("已经收了多少钱", amountPaid) { amountPaid = it }
                }
                MoneyTaskField("以前还欠多少钱（没有就空着）", oldDebt) { oldDebt = it }

                if (identity.role != DeviceRole.DRIVER) {
                    Text("谁去", fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(selectedEmployee == null, { selectedEmployee = null }, { Text("先不定") })
                        }
                        items(employees, key = { it.id }) { employee ->
                            FilterChip(
                                selected = selectedEmployee?.id == employee.id,
                                onClick = { selectedEmployee = employee },
                                label = { Text(employee.name) }
                            )
                        }
                    }
                } else {
                    Text("负责人：${identity.employeeName}", fontWeight = FontWeight.Bold)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(urgent, { urgent = it })
                    Spacer(Modifier.width(8.dp))
                    Text("急单")
                }
                OutlinedTextField(note, { note = it }, label = { Text("备注（可不填）") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val assignee = selectedEmployee
                    onSave(
                        DeliveryTask(
                            customerName = customer,
                            address = address,
                            taskType = type.name,
                            deliveryQuantity = if (type == TaskType.PICKUP_ONLY) 0 else deliveryQty.toIntOrNull() ?: 0,
                            pickupQuantity = pickupQty.toIntOrNull() ?: 0,
                            assignedEmployeeId = assignee?.id ?: identity.employeeId.takeIf { identity.role == DeviceRole.DRIVER },
                            assignedEmployeeRemoteId = assignee?.firestoreId
                                ?: identity.employeeRemoteId.takeIf { identity.role == DeviceRole.DRIVER }.orEmpty(),
                            assignedEmployeeName = assignee?.name
                                ?: identity.employeeName.takeIf { identity.role == DeviceRole.DRIVER }.orEmpty(),
                            paymentStatus = payment.name,
                            amountPaid = amountPaid.toDoubleOrNull() ?: 0.0,
                            debtReminder = oldDebt.toDoubleOrNull() ?: 0.0,
                            priority = if (urgent) TaskPriority.URGENT.name else TaskPriority.NORMAL.name,
                            note = note,
                            createdByEmployeeId = identity.employeeId,
                            createdByName = identity.employeeName,
                            synced = false
                        )
                    )
                },
                enabled = customer.isNotBlank() || address.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun MoneyTaskField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' }) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}
