package com.example.myproductivityapp.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import com.example.myproductivityapp.data.AppDatabase
import com.example.myproductivityapp.data.model.BottleType
import com.example.myproductivityapp.data.model.DeliveryRecord
import com.example.myproductivityapp.data.model.Employee
import com.example.myproductivityapp.data.remote.PhotoUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen() {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }

    // Repositories
    val client = com.example.myproductivityapp.MainActivity.cloudClient
    val deliveryRecordRepo = remember { com.example.myproductivityapp.data.repository.DeliveryRecordRepository(database.deliveryRecordDao(), client) }
    val employeeRepo = remember { com.example.myproductivityapp.data.repository.EmployeeRepository(database.employeeDao(), client) }
    val scope = rememberCoroutineScope()

    var allRecords by remember { mutableStateOf<List<DeliveryRecord>>(emptyList()) }
    var employees by remember { mutableStateOf<List<Employee>>(emptyList()) }
    var filteredRecords by remember { mutableStateOf<List<DeliveryRecord>>(emptyList()) }

    var showFilterDialog by remember { mutableStateOf(false) }
    var selectedEmployee by remember { mutableStateOf<Employee?>(null) }
    var searchKeyword by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf<Long?>(null) }
    var endDate by remember { mutableStateOf<Long?>(null) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var recordToDelete by remember { mutableStateOf<DeliveryRecord?>(null) }
    var recordToEdit by remember { mutableStateOf<DeliveryRecord?>(null) }

    LaunchedEffect(Unit) {
        deliveryRecordRepo.observeAll().collect { records ->
            allRecords = records
            filteredRecords = records
        }
    }

    LaunchedEffect(Unit) {
        employeeRepo.observeAll().collect { empList ->
            employees = empList
        }
    }

    LaunchedEffect(selectedEmployee, searchKeyword, startDate, endDate) {
        filteredRecords = allRecords.filter { record ->
            val matchEmployee = selectedEmployee == null || record.employeeId == selectedEmployee!!.id
            val matchKeyword = searchKeyword.isBlank() ||
                               record.employeeName.contains(searchKeyword, ignoreCase = true) ||
                               record.notes.contains(searchKeyword, ignoreCase = true)
            val matchStartDate = startDate == null || record.date >= startDate!!
            val matchEndDate = endDate == null || record.date <= endDate!!

            matchEmployee && matchKeyword && matchStartDate && matchEndDate
        }
    }

    val totalAmount = filteredRecords.sumOf { it.totalAmount }
    val totalQuantity = filteredRecords.sumOf { it.quantity }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "统计汇总",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "记录数: ${filteredRecords.size} 条",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "总数量: $totalQuantity 瓶",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "总金额: ¥${String.format("%.2f", totalAmount)}",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = "筛选",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (selectedEmployee != null || searchKeyword.isNotBlank() || startDate != null || endDate != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "当前筛选:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (selectedEmployee != null) {
                        Text(
                            text = "• 员工: ${selectedEmployee!!.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (searchKeyword.isNotBlank()) {
                        Text(
                            text = "• 关键字: $searchKeyword",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (startDate != null || endDate != null) {
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        val startStr = startDate?.let { dateFormat.format(Date(it)) } ?: "不限"
                        val endStr = endDate?.let { dateFormat.format(Date(it)) } ?: "不限"
                        Text(
                            text = "• 时间: $startStr 至 $endStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            selectedEmployee = null
                            searchKeyword = ""
                            startDate = null
                            endDate = null
                        }
                    ) {
                        Text("清除筛选")
                    }
                }
            }
        }

        if (filteredRecords.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无配送记录",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredRecords) { record ->
                    RecordCard(
                        record = record,
                        onEdit = { recordToEdit = record },
                        onDelete = { recordToDelete = record },
                        onReturn = {
                            scope.launch {
                                val newStatus = if (record.exchangeStatus == "PENDING") "RETURNED" else "PENDING"
                                deliveryRecordRepo.update(record.copy(exchangeStatus = newStatus))
                            }
                        }
                    )
                }
            }
        }
    }

    if (recordToDelete != null) {
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除这条配送记录吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            deliveryRecordRepo.delete(recordToDelete!!)
                            recordToDelete = null
                        }
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (recordToEdit != null) {
        EditRecordDialog(
            record = recordToEdit!!,
            onDismiss = { recordToEdit = null },
            onConfirm = { updatedRecord ->
                scope.launch {
                    deliveryRecordRepo.update(updatedRecord)
                    recordToEdit = null
                }
            }
        )
    }

    if (showFilterDialog) {
        FilterDialog(
            employees = employees,
            selectedEmployee = selectedEmployee,
            searchKeyword = searchKeyword,
            startDate = startDate,
            endDate = endDate,
            onDismiss = { showFilterDialog = false },
            onConfirm = { emp, keyword, start, end ->
                selectedEmployee = emp
                searchKeyword = keyword
                startDate = start
                endDate = end
                showFilterDialog = false
            }
        )
    }

    if (recordToReturn != null) {
        ReturnDialog(
            record = recordToReturn!!,
            onDismiss = { recordToReturn = null },
            onConfirm = { record ->
                scope.launch {
                    deliveryRecordRepo.update(record)
                    recordToReturn = null
                }
            }
        )
    }
}

@Composable
fun RecordCard(record: DeliveryRecord, onEdit: () -> Unit, onDelete: () -> Unit, onReturn: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    var showImageDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = record.employeeName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateFormat.format(Date(record.date)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "编辑",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Parse and display bottle details
            val detailsText = record.notes
            val parts = detailsText.split(" | ")

            parts.forEach { part ->
                if (part.startsWith("备注:")) {
                    // Skip, will display at the end
                } else if (part.contains("×") && part.contains("¥")) {
                    // Parse bottle type details with price
                    val bottleInfo = part.trim()

                    if (bottleInfo.contains("[对象:")) {
                        // Rental bottle with customer
                        val mainPart = bottleInfo.substringBefore("(").trim()
                        val yearPart = if (bottleInfo.contains("(")) {
                            bottleInfo.substringAfter("(").substringBefore(")").trim()
                        } else ""
                        val customerPart = bottleInfo.substringAfter("[对象:").substringBefore("]").trim()

                        val typeName = mainPart.substringBefore(":").trim()
                        val calculation = mainPart.substringAfter(":").trim()

                        Text(
                            text = "$typeName  对象: $customerPart",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (yearPart.isNotBlank()) {
                            Text(
                                text = "    $yearPart",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "    $calculation",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (bottleInfo.contains("(") && bottleInfo.contains(")")) {
                        // Exchange bottle with years
                        val mainPart = bottleInfo.substringBefore("(").trim()
                        val yearPart = bottleInfo.substringAfter("(").substringBefore(")").trim()

                        val typeName = mainPart.substringBefore(":").trim()
                        val calculation = mainPart.substringAfter(":").trim()

                        Text(
                            text = typeName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (yearPart.isNotBlank()) {
                            Text(
                                text = "    $yearPart",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "    $calculation",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        // Simple bottle type (heavy, new, small)
                        val typeName = bottleInfo.substringBefore(":").trim()
                        val calculation = bottleInfo.substringAfter(":").trim()

                        Text(
                            text = "$typeName  $calculation",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                } else if (part.contains(":") && part.contains("瓶")) {
                    // 对瓶 - only has year info, no price calculation
                    val bottleInfo = part.trim()

                    if (bottleInfo.contains("(") && bottleInfo.contains(")")) {
                        val mainPart = bottleInfo.substringBefore("(").trim()
                        val yearPart = bottleInfo.substringAfter("(").substringBefore(")").trim()

                        val typeName = mainPart.substringBefore(":").trim()
                        val qtyInfo = mainPart.substringAfter(":").trim()

                        Text(
                            text = typeName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (yearPart.isNotBlank()) {
                            Text(
                                text = "    $yearPart",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "    $qtyInfo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // Display notes if exists
            val notePart = parts.find { it.startsWith("备注:") }
            if (notePart != null) {
                val noteContent = notePart.substringAfter("备注:").trim()
                if (noteContent.isNotBlank()) {
                    Text(
                        text = "备注: $noteContent",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Display image if exists（优先服务器压缩图 URL，其次本机文件）
            val remoteUrl0 = PhotoUtil.remoteUrlList(record.remoteImages).firstOrNull()
            val hasImage = remoteUrl0 != null ||
                record.imageUrl.isNotBlank() ||
                (record.imagePath.isNotBlank() && File(record.imagePath).exists())
            if (hasImage) {
                val imageData: Any? = when {
                    remoteUrl0 != null -> remoteUrl0
                    record.imageUrl.startsWith("http") -> record.imageUrl
                    record.imageUrl.isNotBlank() && File(record.imageUrl).exists() -> File(record.imageUrl)
                    record.imagePath.isNotBlank() && File(record.imagePath).exists() -> File(record.imagePath)
                    else -> null
                }
                if (imageData != null) {
                    Image(
                        painter = rememberAsyncImagePainter(imageData),
                        contentDescription = "配送图片",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clickable { showImageDialog = true },
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "总数量: ${record.quantity} 瓶",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "总金额: ¥${String.format("%.2f", record.totalAmount)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    if (record.cashAmount > 0) {
                        Text(
                            text = "现金: ¥${String.format("%.2f", record.cashAmount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (record.wechatAmount > 0) {
                        Text(
                            text = "微信: ¥${String.format("%.2f", record.wechatAmount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (record.debtAmount > 0) {
                        Text(
                            text = "欠款: ¥${String.format("%.2f", record.debtAmount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // 对瓶状态切换
        if (record.exchangeStatus == "PENDING" || record.exchangeStatus == "RETURNED") {
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (record.exchangeStatus == "PENDING") "🔴 未回" else "🟢 已回",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (record.exchangeStatus == "PENDING")
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
                Button(
                    onClick = onReturn,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (record.exchangeStatus == "PENDING")
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text(if (record.exchangeStatus == "PENDING") "归还" else "取消归还")
                }
            }
        }
        }

        val dialogRemoteUrl = PhotoUtil.remoteUrlList(record.remoteImages).firstOrNull()
        if (showImageDialog && (dialogRemoteUrl != null || record.imageUrl.isNotBlank() || record.imagePath.isNotBlank())) {
        Dialog(
            onDismissRequest = { showImageDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { showImageDialog = false }
            ) {
                val imageData: Any? = when {
                    dialogRemoteUrl != null -> dialogRemoteUrl
                    record.imageUrl.startsWith("http") -> record.imageUrl
                    record.imageUrl.isNotBlank() && File(record.imageUrl).exists() -> File(record.imageUrl)
                    record.imagePath.isNotBlank() && File(record.imagePath).exists() -> File(record.imagePath)
                    else -> null
                }
                if (imageData != null) {
                    Image(
                        painter = rememberAsyncImagePainter(imageData),
                        contentDescription = "配送图片",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    if (scale > 1f) {
                                        offset = Offset(
                                            x = (offset.x + pan.x).coerceIn(
                                                -(size.width * (scale - 1) / 2),
                                                size.width * (scale - 1) / 2
                                            ),
                                            y = (offset.y + pan.y).coerceIn(
                                                -(size.height * (scale - 1) / 2),
                                                size.height * (scale - 1) / 2
                                            )
                                        )
                                    } else {
                                        offset = Offset.Zero
                                    }
                                }
                            },
                        contentScale = ContentScale.Fit
                    )
                }

                IconButton(
                    onClick = { showImageDialog = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDialog(
    employees: List<Employee>,
    selectedEmployee: Employee?,
    searchKeyword: String,
    startDate: Long?,
    endDate: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Employee?, String, Long?, Long?) -> Unit
) {
    var tempEmployee by remember { mutableStateOf(selectedEmployee) }
    var tempKeyword by remember { mutableStateOf(searchKeyword) }
    var tempStartDate by remember { mutableStateOf(startDate) }
    var tempEndDate by remember { mutableStateOf(endDate) }
    var expandedEmployee by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("筛选条件") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = expandedEmployee,
                    onExpandedChange = { expandedEmployee = it }
                ) {
                    OutlinedTextField(
                        value = tempEmployee?.name ?: "全部员工",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("选择员工") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedEmployee) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedEmployee,
                        onDismissRequest = { expandedEmployee = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("全部员工") },
                            onClick = {
                                tempEmployee = null
                                expandedEmployee = false
                            }
                        )
                        employees.forEach { employee ->
                            DropdownMenuItem(
                                text = { Text(employee.name) },
                                onClick = {
                                    tempEmployee = employee
                                    expandedEmployee = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = tempKeyword,
                    onValueChange = { tempKeyword = it },
                    label = { Text("关键字搜索") },
                    placeholder = { Text("搜索员工姓名或备注") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text(
                    text = "时间范围",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "开始时间",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { showStartDatePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (tempStartDate != null) {
                                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(tempStartDate!!))
                                } else "选择开始时间",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "结束时间",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { showEndDatePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (tempEndDate != null) {
                                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(tempEndDate!!))
                                } else "选择结束时间",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            val calendar = Calendar.getInstance()
                            calendar.set(Calendar.HOUR_OF_DAY, 0)
                            calendar.set(Calendar.MINUTE, 0)
                            calendar.set(Calendar.SECOND, 0)
                            calendar.set(Calendar.MILLISECOND, 0)
                            tempStartDate = calendar.timeInMillis
                            tempEndDate = System.currentTimeMillis()
                        }
                    ) {
                        Text("今天")
                    }
                    TextButton(
                        onClick = {
                            val calendar = Calendar.getInstance()
                            calendar.add(Calendar.DAY_OF_MONTH, -7)
                            calendar.set(Calendar.HOUR_OF_DAY, 0)
                            calendar.set(Calendar.MINUTE, 0)
                            calendar.set(Calendar.SECOND, 0)
                            calendar.set(Calendar.MILLISECOND, 0)
                            tempStartDate = calendar.timeInMillis
                            tempEndDate = System.currentTimeMillis()
                        }
                    ) {
                        Text("最近7天")
                    }
                    TextButton(
                        onClick = {
                            val calendar = Calendar.getInstance()
                            calendar.add(Calendar.DAY_OF_MONTH, -30)
                            calendar.set(Calendar.HOUR_OF_DAY, 0)
                            calendar.set(Calendar.MINUTE, 0)
                            calendar.set(Calendar.SECOND, 0)
                            calendar.set(Calendar.MILLISECOND, 0)
                            tempStartDate = calendar.timeInMillis
                            tempEndDate = System.currentTimeMillis()
                        }
                    ) {
                        Text("最近30天")
                    }
                }

                if (tempStartDate != null || tempEndDate != null) {
                    TextButton(
                        onClick = {
                            tempStartDate = null
                            tempEndDate = null
                        }
                    ) {
                        Text("清除时间筛选")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(tempEmployee, tempKeyword, tempStartDate, tempEndDate)
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

    if (showStartDatePicker) {
        DateTimePickerDialog(
            initialDateTime = tempStartDate ?: System.currentTimeMillis(),
            onDismiss = { showStartDatePicker = false },
            onConfirm = { selectedTime ->
                tempStartDate = selectedTime
                showStartDatePicker = false
            }
        )
    }

    if (showEndDatePicker) {
        DateTimePickerDialog(
            initialDateTime = tempEndDate ?: System.currentTimeMillis(),
            onDismiss = { showEndDatePicker = false },
            onConfirm = { selectedTime ->
                tempEndDate = selectedTime
                showEndDatePicker = false
            }
        )
    }
}

@Composable
fun DateTimePickerDialog(
    initialDateTime: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val calendar = remember { Calendar.getInstance().apply { timeInMillis = initialDateTime } }
    var selectedYear by remember { mutableStateOf(calendar.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableStateOf(calendar.get(Calendar.MONTH)) }
    var selectedDay by remember { mutableStateOf(calendar.get(Calendar.DAY_OF_MONTH)) }
    var selectedHour by remember { mutableStateOf(calendar.get(Calendar.HOUR_OF_DAY)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择日期和时间") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = selectedYear.toString(),
                        onValueChange = {
                            val year = it.toIntOrNull()
                            if (year != null && year in 2000..2100) {
                                selectedYear = year
                            }
                        },
                        label = { Text("年") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = (selectedMonth + 1).toString(),
                        onValueChange = {
                            val month = it.toIntOrNull()
                            if (month != null && month in 1..12) {
                                selectedMonth = month - 1
                            }
                        },
                        label = { Text("月") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = selectedDay.toString(),
                        onValueChange = {
                            val day = it.toIntOrNull()
                            if (day != null && day in 1..31) {
                                selectedDay = day
                            }
                        },
                        label = { Text("日") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = selectedHour.toString(),
                        onValueChange = {
                            val hour = it.toIntOrNull()
                            if (hour != null && hour in 0..23) {
                                selectedHour = hour
                            }
                        },
                        label = { Text("时") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val resultCalendar = Calendar.getInstance()
                    resultCalendar.set(selectedYear, selectedMonth, selectedDay, selectedHour, 0, 0)
                    resultCalendar.set(Calendar.MILLISECOND, 0)
                    onConfirm(resultCalendar.timeInMillis)
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRecordDialog(
    record: DeliveryRecord,
    onDismiss: () -> Unit,
    onConfirm: (DeliveryRecord) -> Unit
) {
    val context = LocalContext.current

    // Extract the pure note (备注) part from the full notes field
    val parts = record.notes.split(" | ")
    val existingNote = parts.find { it.startsWith("备注:") }?.substringAfter("备注:")?.trim() ?: ""
    // The bottle detail parts (everything that's not 备注)
    val bottleDetails = parts.filter { !it.startsWith("备注:") }.joinToString(" | ")

    var notes by remember { mutableStateOf(existingNote) }
    var cashAmount by remember { mutableStateOf(if (record.cashAmount > 0) record.cashAmount.toString() else "") }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    var photoFile by remember { mutableStateOf<File?>(if (record.imagePath.isNotBlank()) File(record.imagePath) else null) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    // 如果记录已有图片，初始化为 1 以确保首次加载
    var imageRefreshTrigger by remember {
        mutableStateOf(if (record.imagePath.isNotBlank()) 1 else 0)
    }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { _ ->
        val hasContent = photoFile?.let { it.exists() && it.length() > 0 } ?: false
        if (hasContent) {
            imageRefreshTrigger++
        } else {
            photoFile = if (record.imagePath.isNotBlank()) File(record.imagePath) else null
            capturedImageUri = null
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = File(context.filesDir, "delivery_images")
            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }
            val file = File(storageDir, "IMG_${timeStamp}.jpg")
            photoFile = file

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            capturedImageUri = uri
            cameraLauncher.launch(uri)
        } else {
            showPermissionDialog = true
        }
    }

    val wechatAmount = remember(cashAmount) {
        val total = record.totalAmount
        val cash = cashAmount.toDoubleOrNull() ?: 0.0
        val remaining = total - cash
        if (remaining >= 0) remaining else 0.0
    }
    val debtAmount = remember(cashAmount) {
        val total = record.totalAmount
        val cash = cashAmount.toDoubleOrNull() ?: 0.0
        val remaining = total - cash
        if (remaining < 0) -remaining else 0.0
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("补充记录信息") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Summary (read-only)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "${record.employeeName}  ·  ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(record.date))}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "总金额: ¥${String.format("%.2f", record.totalAmount)}  ·  总数量: ${record.quantity} 瓶",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Payment section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "收款信息",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = cashAmount,
                            onValueChange = { cashAmount = it },
                            label = { Text("现金支付 (元)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "微信支付: ¥${String.format("%.2f", wechatAmount)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (debtAmount > 0) {
                                Text(
                                    text = "欠款: ¥${String.format("%.2f", debtAmount)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // Notes section
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("补充备注") },
                    placeholder = { Text("添加补充说明信息...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )

                // Image section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "补充图片",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (photoFile != null) {
                            // 异步加载图片
                            LaunchedEffect(imageRefreshTrigger) {
                                if (imageRefreshTrigger > 0) {
                                    previewBitmap = withContext(Dispatchers.IO) {
                                        photoFile?.absolutePath?.let { path ->
                                            val options = BitmapFactory.Options().apply {
                                                inJustDecodeBounds = true
                                            }
                                            BitmapFactory.decodeFile(path, options)
                                            if (options.outWidth > 0) {
                                                options.inSampleSize = maxOf(1, options.outWidth / 800)
                                                options.inJustDecodeBounds = false
                                                BitmapFactory.decodeFile(path, options)
                                            } else null
                                        }
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                            ) {
                                if (previewBitmap != null) {
                                    Image(
                                        bitmap = previewBitmap!!.asImageBitmap(),
                                        contentDescription = "记录图片",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator()
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                "等待图片写入...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        photoFile = null
                                        capturedImageUri = null
                                        previewBitmap = null
                                    },
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "删除图片",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "拍照")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("拍摄补充图片")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Reconstruct notes: bottle details + supplementary notes
                    val newNotes = buildString {
                        append(bottleDetails)
                        if (notes.isNotBlank()) {
                            append(" | 备注: $notes")
                        }
                    }

                    val cash = cashAmount.toDoubleOrNull() ?: 0.0
                    val updatedRecord = record.copy(
                        notes = newNotes,
                        cashAmount = cash,
                        wechatAmount = wechatAmount,
                        debtAmount = debtAmount,
                        imagePath = photoFile?.absolutePath ?: ""
                    )
                    onConfirm(updatedRecord)
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("需要相机权限") },
            text = { Text("拍摄图片需要相机权限，请在设置中授予相机权限。") },
            confirmButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("确定")
                }
            }
        )
    }
}
