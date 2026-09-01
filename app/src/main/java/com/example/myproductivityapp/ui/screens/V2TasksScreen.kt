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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myproductivityapp.MainActivity
import com.example.myproductivityapp.data.AppDatabase
import com.example.myproductivityapp.data.local.DeviceIdentity
import com.example.myproductivityapp.data.local.DeviceRole
import com.example.myproductivityapp.data.model.DeliveryTask
import com.example.myproductivityapp.data.model.Employee
import com.example.myproductivityapp.data.model.PaymentStatus
import com.example.myproductivityapp.data.model.TaskPriority
import com.example.myproductivityapp.data.model.TaskStatus
import com.example.myproductivityapp.data.model.TaskType
import com.example.myproductivityapp.data.repository.DeliveryTaskRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * V2 待办：录入极简，只记事情 + 任务类型(送气/收瓶) + 派给谁。
 * - 营业员/站长：看全站待办；建待办必须选一个送气员。
 * - 送气员：只看派给自己的待办；自己记的自动归自己。
 * 页面分「未完成」「已完成」两块，全部角色都可修改/删除。
 */
@Composable
fun V2TasksScreen(identity: DeviceIdentity) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()

    var tasks by remember { mutableStateOf<List<DeliveryTask>>(emptyList()) }
    var completedTasks by remember { mutableStateOf<List<DeliveryTask>>(emptyList()) }
    var employees by remember { mutableStateOf<List<Employee>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<DeliveryTask?>(null) }
    var deletingTask by remember { mutableStateOf<DeliveryTask?>(null) }

    val isDriver = identity.role == DeviceRole.DRIVER

    LaunchedEffect(identity) {
        val remoteId = identity.employeeRemoteId
        if (isDriver && remoteId.isNotBlank()) {
            // 送气员只看自己的待办（服务器稳定 ID，跨手机不串人）
            db.deliveryTaskDao().observeOpenTasksForEmployeeRemote(remoteId).collect { tasks = it }
        } else if (isDriver) {
            // remoteId 缺失时不清空列表，靠 V2IdentityGate 自动修复或重新绑定；
            // 这里不再用本地 Long ID 兜底——跨手机本地自增 ID 不一致，会导致收不到营业员派单。
            tasks = emptyList()
        } else {
            // 营业员/站长看全站
            db.deliveryTaskDao().observeOpenTasks().collect { tasks = it }
        }
    }
    LaunchedEffect(identity) {
        val remoteId = identity.employeeRemoteId
        if (isDriver && remoteId.isNotBlank()) {
            db.deliveryTaskDao().observeCompletedTasksForEmployeeRemote(remoteId).collect { completedTasks = it }
        } else if (isDriver) {
            completedTasks = emptyList()
        } else {
            db.deliveryTaskDao().observeCompletedTasks().collect { completedTasks = it }
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
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                item { Text("未完成", fontSize = 21.sp, fontWeight = FontWeight.Bold) }
                if (tasks.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "现在没有未完成任务",
                                modifier = Modifier.padding(24.dp),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    items(tasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            isDriver = isDriver,
                            isCompleted = false,
                            onComplete = {
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
                            },
                            onEdit = { editingTask = task },
                            onDelete = { deletingTask = task }
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(4.dp))
                    Text("已完成", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                }
                if (completedTasks.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "还没有完成的任务",
                                modifier = Modifier.padding(24.dp),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    items(completedTasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            isDriver = isDriver,
                            isCompleted = true,
                            onComplete = null,
                            onEdit = { editingTask = task },
                            onDelete = { deletingTask = task }
                        )
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

    editingTask?.let { task ->
        EditTaskDialog(
            identity = identity,
            employees = employees,
            task = task,
            onDismiss = { editingTask = null },
            onSave = { updated ->
                scope.launch {
                    DeliveryTaskRepository(
                        db.deliveryTaskDao(),
                        MainActivity.cloudClient
                    ).update(updated)
                    editingTask = null
                }
            }
        )
    }

    deletingTask?.let { task ->
        AlertDialog(
            onDismissRequest = { deletingTask = null },
            title = { Text("删除待办", fontSize = 22.sp, fontWeight = FontWeight.Bold) },
            text = { Text("确定删除这条待办？", fontSize = 19.sp) },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        DeliveryTaskRepository(
                            db.deliveryTaskDao(),
                            MainActivity.cloudClient
                        ).delete(task)
                        deletingTask = null
                    }
                }) { Text("删除", fontSize = 19.sp) }
            },
            dismissButton = {
                TextButton(onClick = { deletingTask = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun TaskCard(
    task: DeliveryTask,
    isDriver: Boolean,
    isCompleted: Boolean,
    onComplete: (() -> Unit)?,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    // 瓶子日期/厂检标记（"瓶子:xx"）从 note 单独拆出来展示，不算备注正文。
    val bottleInfo = bottleInfoFromNote(task.note)
    val extraNote = noteWithoutBottle(task.note)
    // 新建的普通待办只带默认值，极简显示；旧的特殊任务仍展示详情，避免信息丢失。
    // note 里的"瓶子:xx"不算额外内容，新建待办仍走极简样式。
    val simpleReminder = (task.taskType == TaskType.DELIVERY.name || task.taskType == TaskType.PICKUP_ONLY.name) &&
        task.address.isBlank() &&
        task.deliveryQuantity == 0 &&
        task.pickupQuantity == 0 &&
        task.paymentStatus == PaymentStatus.UNPAID.name &&
        task.amountToCollect == 0.0 &&
        task.amountPaid == 0.0 &&
        task.debtReminder == 0.0 &&
        task.priority == TaskPriority.NORMAL.name &&
        extraNote.isBlank()
    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    val typeText = when (task.taskType) {
        TaskType.DELIVERY.name -> "送气"
        TaskType.PICKUP_ONLY.name -> "收瓶"
        TaskType.CUSTOMER_DROPOFF.name -> "客户已拿瓶到站"
        TaskType.RENTAL.name -> "租瓶"
        TaskType.EXCHANGE.name -> "换瓶"
        else -> "送气"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                task.customerName.ifBlank { "未写客户" },
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(typeText, fontWeight = FontWeight.Medium)

            // 营业员/站长端显示负责人；送气员端不用显示（一定是他自己）
            if (!isDriver && task.assignedEmployeeName.isNotBlank()) {
                Text("负责人：${task.assignedEmployeeName}", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }

            // 瓶子日期/厂检标记：送气工端和营业员端都展示
            if (bottleInfo != null) {
                Text(bottleInfo, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }

            Text(
                "创建 ${timeFormat.format(Date(task.createdAt))}",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isCompleted && task.completedAt != null) {
                Text(
                    "完成 ${timeFormat.format(Date(task.completedAt))}",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 旧的特殊任务仍保留详细展示；新建的普通待办只显示客户姓名。
            if (!simpleReminder) {
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

                if (extraNote.isNotBlank()) Text("备注：$extraNote")
            }

            if (onComplete != null) {
                Button(onClick = onComplete, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("完成", fontSize = 20.sp)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEdit) { Text("修改", fontSize = 18.sp) }
                TextButton(onClick = onDelete) { Text("删除", fontSize = 18.sp) }
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
    var taskType by remember { mutableStateOf(TaskType.DELIVERY.name) }
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
                    label = { Text("待办的事情") },
                    placeholder = { Text("例如：给黄叔送两瓶气") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 21.sp)
                )

                Text("任务类型", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = taskType == TaskType.DELIVERY.name,
                        onClick = { taskType = TaskType.DELIVERY.name },
                        label = { Text("送气", fontSize = 17.sp) }
                    )
                    FilterChip(
                        selected = taskType == TaskType.PICKUP_ONLY.name,
                        onClick = { taskType = TaskType.PICKUP_ONLY.name },
                        label = { Text("收瓶", fontSize = 17.sp) }
                    )
                }

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
                            taskType = taskType,
                            // 普通待办不猜数量/付款/地址，全部默认值
                            deliveryQuantity = 0,
                            pickupQuantity = 0,
                            // 营业员指派 = 选中送气员；送气员自记 = 自动绑自己
                            assignedEmployeeId = assignee?.id ?: identity.employeeId.takeIf { isDriver },
                            assignedEmployeeRemoteId = assignee?.firestoreId
                                ?: identity.employeeRemoteId.takeIf { isDriver }.orEmpty(),
                            assignedEmployeeName = assignee?.name
                                ?: identity.employeeName.takeIf { isDriver }.orEmpty(),
                            note = "",
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

@Composable
private fun EditTaskDialog(
    identity: DeviceIdentity,
    employees: List<Employee>,
    task: DeliveryTask,
    onDismiss: () -> Unit,
    onSave: (DeliveryTask) -> Unit
) {
    var customer by remember { mutableStateOf(task.customerName) }
    var taskType by remember {
        mutableStateOf(if (task.taskType == TaskType.DELIVERY.name) TaskType.DELIVERY.name else TaskType.PICKUP_ONLY.name)
    }
    var completed by remember { mutableStateOf(task.status == TaskStatus.COMPLETED.name) }
    val isDriver = identity.role == DeviceRole.DRIVER

    // 初始选中当前负责人（送气员端不显示，保持 assignee 不变）
    var selectedEmployee by remember(task) {
        mutableStateOf(
            employees.firstOrNull { employee ->
                if (task.assignedEmployeeRemoteId.isNotBlank()) employee.firestoreId == task.assignedEmployeeRemoteId
                else if (task.assignedEmployeeId != null) employee.id == task.assignedEmployeeId
                else false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改待办", fontSize = 24.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = customer,
                    onValueChange = { customer = it },
                    label = { Text("待办的事情") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 21.sp)
                )

                Text("任务类型", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = taskType == TaskType.DELIVERY.name,
                        onClick = { taskType = TaskType.DELIVERY.name },
                        label = { Text("送气", fontSize = 17.sp) }
                    )
                    FilterChip(
                        selected = taskType == TaskType.PICKUP_ONLY.name,
                        onClick = { taskType = TaskType.PICKUP_ONLY.name },
                        label = { Text("收瓶", fontSize = 17.sp) }
                    )
                }

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

                Text("状态", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !completed,
                        onClick = { completed = false },
                        label = { Text("未完成") }
                    )
                    FilterChip(
                        selected = completed,
                        onClick = { completed = true },
                        label = { Text("已完成") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val assignee = selectedEmployee
                    onSave(
                        task.copy(
                            customerName = customer.trim(),
                            taskType = taskType,
                            // 送气员端不显示派给谁，保持原 assignee 不变
                            assignedEmployeeId = if (!isDriver) assignee?.id ?: task.assignedEmployeeId else task.assignedEmployeeId,
                            assignedEmployeeRemoteId = if (!isDriver) assignee?.firestoreId ?: task.assignedEmployeeRemoteId else task.assignedEmployeeRemoteId,
                            assignedEmployeeName = if (!isDriver) assignee?.name ?: task.assignedEmployeeName else task.assignedEmployeeName,
                            note = task.note,
                            status = if (completed) TaskStatus.COMPLETED.name else TaskStatus.PENDING.name,
                            completedAt = if (completed) System.currentTimeMillis() else null
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

/** 从 note 里提取"瓶子:xx"标记段（新建待办的瓶子日期/厂检标记），没有则返回 null。 */
private fun bottleInfoFromNote(note: String): String? {
    if (note.isBlank()) return null
    return note.split(" | ")
        .mapNotNull { seg ->
            val s = seg.trim()
            if (s.startsWith("瓶子:")) {
                val v = s.removePrefix("瓶子:").trim()
                if (v.isNotBlank()) "瓶子:$v" else null
            } else null
        }
        .firstOrNull()
}

/** 去掉 note 里的"瓶子:xx"段，用于判断是否极简待办、展示其余备注。 */
private fun noteWithoutBottle(note: String): String {
    if (note.isBlank()) return ""
    return note.split(" | ")
        .filterNot { it.trim().startsWith("瓶子:") }
        .joinToString(" | ")
        .trim()
}
