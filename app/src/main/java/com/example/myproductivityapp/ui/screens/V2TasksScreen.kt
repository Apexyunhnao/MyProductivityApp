package com.example.myproductivityapp.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
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
import com.example.myproductivityapp.data.remote.PhotoUtil
import com.example.myproductivityapp.data.repository.DeliveryTaskRepository
import com.example.myproductivityapp.ui.components.PhotoViewerDialog
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * V2 待办：录入极简，只记事情 + 任务类型(送气/收瓶) + 派给谁。
 * - 营业员/站长：看全站待办；建待办必须选一个送气员。不发待办的人不点完成——完成按钮只有送气工有。
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
    // 删除提交中锁：防止网络卡顿时连点删除重复提交
    var deleting by remember { mutableStateOf(false) }
    // 筛选：关键词 / 时间范围 / 排序
    var searchText by remember { mutableStateOf("") }
    var fromDateText by remember { mutableStateOf("") }
    var toDateText by remember { mutableStateOf("") }
    var sortDesc by remember { mutableStateOf(true) }
    // 折叠状态：天（key=当天零点）、天内员工（key="$day:$emp"）
    val expandedOpenDays = remember { mutableStateMapOf<Long, Boolean>() }
    val expandedDoneDays = remember { mutableStateMapOf<Long, Boolean>() }
    val expandedDayEmployees = remember { mutableStateMapOf<String, Boolean>() }

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

    // —— 筛选：关键词 / 时间范围 / 排序 / 按天分组 ——
    fun taskDayMillis(text: String, endOfDay: Boolean): Long? {
        if (text.isBlank()) return null
        val parts = text.trim().split("-")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val mo = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        val cal = Calendar.getInstance().apply {
            clear()
            set(y, mo - 1, d, if (endOfDay) 23 else 0, if (endOfDay) 59 else 0, if (endOfDay) 59 else 0)
        }
        return cal.timeInMillis
    }
    val fromMs = taskDayMillis(fromDateText, false)
    val toMs = taskDayMillis(toDateText, true)
    val keyword = searchText.trim()
    fun taskHit(t: DeliveryTask): Boolean {
        if (keyword.isEmpty()) return true
        return t.customerName.contains(keyword) || t.note.contains(keyword) ||
            t.assignedEmployeeName.contains(keyword) || t.address.contains(keyword)
    }
    fun taskInRange(ts: Long): Boolean =
        (fromMs == null || ts >= fromMs) && (toMs == null || ts <= toMs)

    val visibleOpen = tasks.filter { taskHit(it) && taskInRange(it.createdAt) }
    val visibleCompleted = completedTasks.filter { taskHit(it) && taskInRange(it.completedAt ?: it.createdAt) }

    // 未完成默认按时间正序展示（先建先做），切倒序则最新在前；已完成按完成时间。
    fun sortTasks(list: List<DeliveryTask>): List<DeliveryTask> =
        if (sortDesc) list.sortedByDescending { it.createdAt } else list.sortedBy { it.createdAt }
    val openSorted = sortTasks(visibleOpen)
    val doneSorted = if (sortDesc) {
        visibleCompleted.sortedByDescending { it.completedAt ?: it.createdAt }
    } else {
        visibleCompleted.sortedBy { it.completedAt ?: it.createdAt }
    }

    // 按天分组；天序随排序（倒序=新天在前）
    fun groupDays(list: List<DeliveryTask>, at: (DeliveryTask) -> Long): List<Pair<Long, List<DeliveryTask>>> {
        val map = LinkedHashMap<Long, MutableList<DeliveryTask>>()
        val cal = Calendar.getInstance()
        for (t in list) {
            cal.timeInMillis = at(t)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            map.getOrPut(cal.timeInMillis) { mutableListOf() }.add(t)
        }
        val sorted = if (sortDesc) map.toList().sortedByDescending { it.first } else map.toList().sortedBy { it.first }
        return sorted
    }
    val openDayGroups = groupDays(openSorted) { it.createdAt }
    val doneDayGroups = groupDays(doneSorted) { it.completedAt ?: it.createdAt }

    // 标记完成/已回：写本地 + 标记未同步，后台 flush 补传
    fun markComplete(task: DeliveryTask) {
        scope.launch {
            try {
                db.deliveryTaskDao().update(
                    task.copy(
                        status = TaskStatus.COMPLETED.name,
                        completedAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        synced = false
                    )
                )
                Toast.makeText(context, "已完成", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "操作失败：${e.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
            }
        }
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
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = searchText,
                                onValueChange = { searchText = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("搜事情/负责人/备注") },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 17.sp)
                            )
                            OutlinedButton(onClick = { sortDesc = !sortDesc }) {
                                Text(if (sortDesc) "新→旧 ↓" else "旧→新 ↑", fontSize = 15.sp)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = fromDateText,
                                onValueChange = { fromDateText = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("从 2026-09-01") },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 15.sp)
                            )
                            OutlinedTextField(
                                value = toDateText,
                                onValueChange = { toDateText = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("到 2026-09-30") },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 15.sp)
                            )
                        }
                        if (searchText.isNotBlank() || fromDateText.isNotBlank() || toDateText.isNotBlank()) {
                            TextButton(onClick = {
                                searchText = ""
                                fromDateText = ""
                                toDateText = ""
                            }) { Text("清除筛选", fontSize = 15.sp) }
                        }
                    }
                }
                item { Text("未完成", fontSize = 21.sp, fontWeight = FontWeight.Bold) }
                if (openDayGroups.isEmpty()) {
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
                } else if (isDriver) {
                    // 送气工：未完成的待办全部平铺、不折叠（一眼看清接下来要做什么）；已完成才折叠
                    items(openSorted, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            isDriver = true,
                            isCompleted = false,
                            // 送气工不能完成瓶子状态待办（瓶已回/瓶未回只能营业员/站长处理）
                            onComplete = if (task.bottleStatus.isEmpty() && task.taskType != TaskType.EXCHANGE.name) {
                                { markComplete(task) }
                            } else null,
                            onEdit = { editingTask = task },
                            onDelete = { deletingTask = task }
                        )
                    }
                } else {
                    // 营业员/站长：未完成按天折叠，天内按负责人分组（原样不变）
                    openDayGroups.forEach { (day, dayList) ->
                        val dayExpanded = expandedOpenDays[day] == true
                        item(key = "od-$day") {
                            GroupHeader(
                                title = SimpleDateFormat("M月d日 EEEE", Locale.CHINESE).format(Date(day)),
                                count = dayList.size,
                                expanded = dayExpanded,
                                onClick = { expandedOpenDays[day] = !dayExpanded }
                            )
                        }
                        if (dayExpanded) {
                            val byEmp = dayList.groupBy { it.assignedEmployeeName.ifBlank { "未指派" } }
                                .toList()
                                .sortedByDescending { (_, l) -> l.size }
                            byEmp.forEach { (emp, empList) ->
                                val ekey = "$day:$emp"
                                item(key = "oe-$ekey") {
                                    GroupHeader(
                                        title = emp,
                                        count = empList.size,
                                        expanded = expandedDayEmployees[ekey] == true,
                                        onClick = { expandedDayEmployees[ekey] = expandedDayEmployees[ekey] != true }
                                    )
                                }
                                if (expandedDayEmployees[ekey] == true) {
                                    items(empList, key = { it.id }) { task ->
                                        TaskCard(
                                            task = task,
                                            isDriver = false,
                                            isCompleted = false,
                                            onComplete = { markComplete(task) },
                                            onEdit = { editingTask = task },
                                            onDelete = { deletingTask = task }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(4.dp))
                    Text("已完成", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                }
                if (doneDayGroups.isEmpty()) {
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
                    doneDayGroups.forEach { (day, dayList) ->
                        val dayExpanded = expandedDoneDays[day] == true
                        item(key = "dd-$day") {
                            GroupHeader(
                                title = SimpleDateFormat("M月d日 EEEE", Locale.CHINESE).format(Date(day)),
                                count = dayList.size,
                                expanded = dayExpanded,
                                onClick = { expandedDoneDays[day] = !dayExpanded }
                            )
                        }
                        if (dayExpanded) {
                            items(dayList, key = { it.id }) { task ->
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
        }
    }

    if (showAddDialog) {
        AddTaskDialog(
            identity = identity,
            employees = employees,
            onDismiss = { showAddDialog = false },
            onSave = { task ->
                scope.launch {
                    // 添加即同步：直接走 repository.save（先存本地，再传服务器，失败静默留本地等补传）
                    try {
                        val (_, cloudOk) = DeliveryTaskRepository(
                            db.deliveryTaskDao(),
                            MainActivity.cloudClient
                        ).saveWithResult(task)
                        Toast.makeText(
                            context,
                            if (cloudOk) "保存成功" else "已保存，服务器没连上，稍后自动同步",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "保存失败：${e.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                    }
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
                    try {
                        val (_, cloudOk) = DeliveryTaskRepository(
                            db.deliveryTaskDao(),
                            MainActivity.cloudClient
                        ).saveWithResult(updated)
                        Toast.makeText(
                            context,
                            if (cloudOk) "修改成功" else "修改已保存，服务器没连上，稍后自动同步",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "保存失败：${e.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                    }
                    editingTask = null
                }
            }
        )
    }

    deletingTask?.let { task ->
        AlertDialog(
            onDismissRequest = { if (!deleting) deletingTask = null },
            title = { Text("删除待办", fontSize = 22.sp, fontWeight = FontWeight.Bold) },
            text = { Text("确定删除这条待办？", fontSize = 19.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        deleting = true
                        scope.launch {
                            try {
                                val cloudOk = DeliveryTaskRepository(
                                    db.deliveryTaskDao(),
                                    MainActivity.cloudClient
                                ).deleteWithResult(task)
                                Toast.makeText(
                                    context,
                                    if (cloudOk) "删除成功" else "已删除，服务器没连上，稍后自动同步",
                                    Toast.LENGTH_LONG
                                ).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "删除失败：${e.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                            }
                            deletingTask = null
                            deleting = false
                        }
                    },
                    enabled = !deleting
                ) { Text(if (deleting) "删除中…" else "删除", fontSize = 19.sp) }
            },
            dismissButton = {
                TextButton(onClick = { deletingTask = null }, enabled = !deleting) { Text("取消") }
            }
        )
    }
}

@Composable
private fun GroupHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$count 条", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (expanded) "▲" else "▼", fontSize = 16.sp)
            }
        }
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
    // 点按照片放大查看
    var zoomPhoto by remember { mutableStateOf(false) }
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

    val isExchange = task.taskType == TaskType.EXCHANGE.name
    val typeText = when (task.taskType) {
        TaskType.DELIVERY.name -> "送气"
        TaskType.PICKUP_ONLY.name -> "收瓶"
        TaskType.CUSTOMER_DROPOFF.name -> "客户已拿瓶到站"
        TaskType.RENTAL.name -> "租瓶"
        TaskType.EXCHANGE.name -> "归还对瓶"
        else -> "送气"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                task.customerName.ifBlank { "未写客户" },
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(typeText, fontWeight = FontWeight.Medium)
                // 瓶状态标签：瓶未回（红）/ 瓶已回（绿）；老 EXCHANGE 类型按状态兼容显示
                val statusLabel = when {
                    task.bottleStatus == "RETURNED" -> "瓶已回" to Color(0xFF2E7D32)
                    task.bottleStatus == "NOT_RETURNED" -> "瓶未回" to MaterialTheme.colorScheme.error
                    isExchange && task.status == TaskStatus.COMPLETED.name -> "已回" to Color(0xFF2E7D32)
                    isExchange -> "未回" to MaterialTheme.colorScheme.error
                    else -> null
                }
                if (statusLabel != null) {
                    Text(
                        statusLabel.first,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusLabel.second
                    )
                }
            }

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

            // 待办照片：优先服务器 URL（跨手机可见），未上传的用本机原图；点按放大查看
            val taskRemoteUrl = PhotoUtil.remoteUrlList(task.remoteImages).firstOrNull()
            val taskPhotoModel: Any? = when {
                taskRemoteUrl != null -> taskRemoteUrl
                task.imagePath.isNotBlank() && File(task.imagePath).exists() -> File(task.imagePath)
                else -> null
            }
            if (taskPhotoModel != null) {
                AsyncImage(
                    model = taskPhotoModel,
                    contentDescription = "待办照片，点按放大",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clickable { zoomPhoto = true },
                    contentScale = ContentScale.Crop
                )
                Text(
                    "点按照片可放大",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (zoomPhoto && taskPhotoModel != null) {
                PhotoViewerDialog(imageData = taskPhotoModel, onDismiss = { zoomPhoto = false })
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
    var noteText by remember { mutableStateOf("") }
    // 瓶状态：""普通 / NOT_RETURNED 瓶未回 / RETURNED 瓶已回（仅营业员/站长可选）
    var bottleStatus by remember { mutableStateOf("") }
    // 拍照
    val context = LocalContext.current
    var imagePath by remember { mutableStateOf("") }
    var photoFile by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) photoFile?.let { if (it.exists() && it.length() > 0) imagePath = it.absolutePath }
    }
    fun takePhoto() {
        val dir = File(context.filesDir, "delivery_images")
        if (!dir.exists()) dir.mkdirs()
        val f = File(dir, "IMG_${System.currentTimeMillis()}.jpg")
        photoFile = f
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
        cameraLauncher.launch(uri)
    }
    val isDriver = identity.role == DeviceRole.DRIVER
    // 提交中锁：防止网络卡顿时连点保存产生重复待办
    var saving by remember { mutableStateOf(false) }

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
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
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
                    Text("瓶状态", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = bottleStatus == "NOT_RETURNED",
                            onClick = {
                                bottleStatus =
                                    if (bottleStatus == "NOT_RETURNED") "" else "NOT_RETURNED"
                            },
                            label = { Text("瓶未回", fontSize = 15.sp) }
                        )
                        FilterChip(
                            selected = bottleStatus == "RETURNED",
                            onClick = {
                                bottleStatus = if (bottleStatus == "RETURNED") "" else "RETURNED"
                            },
                            label = { Text("瓶已回", fontSize = 15.sp) }
                        )
                    }
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

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    textStyle = LocalTextStyle.current.copy(fontSize = 17.sp)
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { takePhoto() }) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (imagePath.isNotBlank()) "重拍照片" else "拍照片", fontSize = 16.sp)
                    }
                    if (imagePath.isNotBlank()) {
                        AsyncImage(
                            model = File(imagePath),
                            contentDescription = "待办照片",
                            modifier = Modifier.size(width = 110.dp, height = 82.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    saving = true
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
                            note = noteText.trim(),
                            bottleStatus = bottleStatus,
                            imagePath = imagePath,
                            createdByEmployeeId = identity.employeeId,
                            createdByName = identity.employeeName,
                            synced = false
                        )
                    )
                },
                // 提交中禁用；营业员/站长必须选了送气员才能保存
                enabled = !saving && customer.isNotBlank() && (isDriver || selectedEmployee != null)
            ) {
                Text(if (saving) "保存中…" else "保存", fontSize = 19.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
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
        mutableStateOf(
            if (task.taskType == TaskType.DELIVERY.name || task.taskType == TaskType.PICKUP_ONLY.name ||
                (task.taskType == TaskType.EXCHANGE.name)
            ) task.taskType
            else TaskType.DELIVERY.name
        )
    }
    var noteText by remember { mutableStateOf(task.note) }
    var bottleStatus by remember { mutableStateOf(task.bottleStatus) }
    var completed by remember { mutableStateOf(task.status == TaskStatus.COMPLETED.name) }
    val isDriver = identity.role == DeviceRole.DRIVER
    // 提交中锁：防止网络卡顿时连点保存
    var saving by remember { mutableStateOf(false) }
    // 送气工编辑瓶子状态待办：不能标完成（防止自证"已拿回"）
    val isExchangeTask = task.taskType == TaskType.EXCHANGE.name
    val canChangeStatus = !(isDriver && (task.bottleStatus.isNotEmpty() || isExchangeTask))
    // 拍照
    val context = LocalContext.current
    var imagePath by remember { mutableStateOf(task.imagePath) }
    var photoFile by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) photoFile?.let { if (it.exists() && it.length() > 0) imagePath = it.absolutePath }
    }
    fun takePhoto() {
        val dir = File(context.filesDir, "delivery_images")
        if (!dir.exists()) dir.mkdirs()
        val f = File(dir, "IMG_${System.currentTimeMillis()}.jpg")
        photoFile = f
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
        cameraLauncher.launch(uri)
    }

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
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
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
                    Text("瓶状态", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = bottleStatus == "NOT_RETURNED",
                            onClick = {
                                bottleStatus =
                                    if (bottleStatus == "NOT_RETURNED") "" else "NOT_RETURNED"
                            },
                            label = { Text("瓶未回", fontSize = 15.sp) }
                        )
                        FilterChip(
                            selected = bottleStatus == "RETURNED",
                            onClick = {
                                bottleStatus = if (bottleStatus == "RETURNED") "" else "RETURNED"
                            },
                            label = { Text("瓶已回", fontSize = 15.sp) }
                        )
                    }
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

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    textStyle = LocalTextStyle.current.copy(fontSize = 17.sp)
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { takePhoto() }) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (imagePath.isNotBlank()) "重拍照片" else "拍照片", fontSize = 16.sp)
                    }
                    if (imagePath.isNotBlank()) {
                        AsyncImage(
                            model = File(imagePath),
                            contentDescription = "待办照片",
                            modifier = Modifier.size(width = 110.dp, height = 82.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                if (canChangeStatus) {
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    saving = true
                    val assignee = selectedEmployee
                    onSave(
                        task.copy(
                            customerName = customer.trim(),
                            taskType = taskType,
                            // 送气员端不显示派给谁，保持原 assignee 不变
                            assignedEmployeeId = if (!isDriver) assignee?.id ?: task.assignedEmployeeId else task.assignedEmployeeId,
                            assignedEmployeeRemoteId = if (!isDriver) assignee?.firestoreId ?: task.assignedEmployeeRemoteId else task.assignedEmployeeRemoteId,
                            assignedEmployeeName = if (!isDriver) assignee?.name ?: task.assignedEmployeeName else task.assignedEmployeeName,
                            note = noteText.trim(),
                            bottleStatus = bottleStatus,
                            imagePath = imagePath,
                            status = if (completed) TaskStatus.COMPLETED.name else TaskStatus.PENDING.name,
                            completedAt = if (completed) System.currentTimeMillis() else null
                        )
                    )
                },
                enabled = !saving && customer.isNotBlank()
            ) {
                Text(if (saving) "保存中…" else "保存", fontSize = 19.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
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
