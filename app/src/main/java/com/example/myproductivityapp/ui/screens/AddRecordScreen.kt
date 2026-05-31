// 包名：定义这个文件属于哪个包
package com.example.myproductivityapp.ui.screens

// 导入Android相机权限相关的类
import android.Manifest
import android.net.Uri
// 导入用于启动相机和请求权限的工具
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
// 导入Compose UI组件
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// 导入文件提供者，用于安全地分享文件给相机应用
import androidx.core.content.FileProvider
// 导入导航控制器，用于页面跳转
import androidx.navigation.NavHostController
// 导入Coil图片加载库
import android.graphics.Bitmap
import android.graphics.BitmapFactory
// 导入数据库和数据模型
import com.example.myproductivityapp.data.AppDatabase
import com.example.myproductivityapp.data.model.BottleType
import com.example.myproductivityapp.data.model.BottleYear
import com.example.myproductivityapp.data.model.DeliveryRecord
import com.example.myproductivityapp.data.model.Employee
// 导入协程，用于异步操作
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 煤气瓶类型项数据类
 * 用于存储每种煤气瓶类型的输入信息
 * @param type 煤气瓶类型（重瓶、租瓶、对瓶、新瓶、小瓶）
 * @param quantity 数量（字符串形式，方便输入）
 * @param price 单价（字符串形式，方便输入）
 * @param yearSelections 年份选择（例如：22厂、26检等）
 * @param customerName 客户名称（仅租瓶需要）
 */
data class BottleTypeItem(
    val type: BottleType,
    val quantity: String,
    val price: String,
    val yearSelections: Map<String, String> = emptyMap(),
    val customerName: String = ""
)

/**
 * 步骤指示器
 * 显示"步骤1/4 → 步骤2/4 → ..."
 */
@Composable
fun StepIndicator(currentStep: Int, totalSteps: Int = 4) {
    val stepLabels = listOf("选择员工", "填写瓶型", "收款备注", "确认保存")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        stepLabels.forEachIndexed { index, label ->
            val isActive = index + 1 == currentStep
            val isDone = index + 1 < currentStep
            val color = when {
                isActive -> MaterialTheme.colorScheme.primary
                isDone -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.outline
            }
            val bgColor = when {
                isActive -> MaterialTheme.colorScheme.primaryContainer
                isDone -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = bgColor
            ) {
                Text(
                    text = if (isDone) "✓ $label" else label,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    color = color,
                    fontSize = 16.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                )
            }
            if (index < stepLabels.size - 1) {
                Text(
                    text = " → ",
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecordScreen(navController: NavHostController) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()

    // Repositories
    val client = com.example.myproductivityapp.MainActivity.cloudClient
    val employeeRepo = remember { com.example.myproductivityapp.data.repository.EmployeeRepository(database.employeeDao(), client) }
    val bottleYearRepo = remember { com.example.myproductivityapp.data.repository.BottleYearRepository(database.bottleYearDao(), client) }
    val priceConfigRepo = remember { com.example.myproductivityapp.data.repository.PriceConfigRepository(database.priceConfigDao(), client) }
    val deliveryRecordRepo = remember { com.example.myproductivityapp.data.repository.DeliveryRecordRepository(database.deliveryRecordDao(), client) }

    // 步骤控制
    var currentStep by remember { mutableStateOf(1) }

    // 数据状态
    var employees by remember { mutableStateOf<List<Employee>>(emptyList()) }
    var selectedEmployee by remember { mutableStateOf<Employee?>(null) }
    var expandedEmployee by remember { mutableStateOf(false) }

    var selectedTypes by remember { mutableStateOf(setOf<BottleType>()) }
    var bottleItems by remember { mutableStateOf(mapOf<BottleType, BottleTypeItem>()) }
    var availableYears by remember { mutableStateOf<List<BottleYear>>(emptyList()) }

    var totalAmount by remember { mutableStateOf(0.0) }
    var cashAmount by remember { mutableStateOf("") }
    var wechatAmount by remember { mutableStateOf(0.0) }
    var debtAmount by remember { mutableStateOf(0.0) }
    var notes by remember { mutableStateOf("") }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    var photoFile by remember { mutableStateOf<File?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var imageRefreshTrigger by remember { mutableStateOf(0) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // 保存成功提示
    var showSaveSnackbar by remember { mutableStateOf(false) }

    // 先定义相机启动器
    // 注意: TakePicture 的 success 在很多国产手机上不可靠（拍照成功也可能返回 false）
    // 因此不依赖 success 标志，而是直接检查文件是否有内容
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { _ ->
        // 检查文件是否真的有内容（文件存在且大小 > 0）
        val hasContent = photoFile?.let { it.exists() && it.length() > 0 } ?: false
        if (hasContent) {
            // 文件有内容，触发刷新显示
            imageRefreshTrigger++
        } else {
            // 拍照失败或文件为空，清理状态
            photoFile?.delete()
            photoFile = null
            capturedImageUri = null
        }
    }

    // 后定义权限启动器
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

    // 数据加载
    LaunchedEffect(Unit) {
        employeeRepo.observeAll().collect { employeeList ->
            employees = employeeList
        }
    }

    LaunchedEffect(Unit) {
        bottleYearRepo.observeAll().collect { years ->
            availableYears = years.distinctBy { "${it.year}${it.type}" }
        }
    }

    LaunchedEffect(Unit) {
        scope.launch {
            val priceConfig = priceConfigRepo.getByType(BottleType.HEAVY.name)
            val heavyItem = BottleTypeItem(
                type = BottleType.HEAVY,
                quantity = "",
                price = priceConfig?.price?.toString() ?: "0"
            )
            bottleItems = mapOf(BottleType.HEAVY to heavyItem)
        }
    }

    LaunchedEffect(selectedTypes) {
        scope.launch {
            val newItems = mutableMapOf<BottleType, BottleTypeItem>()
            newItems[BottleType.HEAVY] = bottleItems[BottleType.HEAVY] ?: BottleTypeItem(
                type = BottleType.HEAVY,
                quantity = "",
                price = "0"
            )

            selectedTypes.forEach { type ->
                if (type != BottleType.HEAVY) {
                    val existingItem = bottleItems[type]
                    if (existingItem != null) {
                        newItems[type] = existingItem
                    } else {
                        val priceConfig = priceConfigRepo.getByType(type.name)
                        newItems[type] = BottleTypeItem(
                            type = type,
                            quantity = "",
                            price = priceConfig?.price?.toString() ?: "0"
                        )
                    }
                }
            }
            bottleItems = newItems
        }
    }

    LaunchedEffect(bottleItems) {
        var total = 0.0
        bottleItems.values.forEach { item ->
            if (item.type != BottleType.EXCHANGE) {
                val qty = item.quantity.toDoubleOrNull() ?: 0.0
                val price = item.price.toDoubleOrNull() ?: 0.0
                total += qty * price
            }
        }
        totalAmount = total
    }

    LaunchedEffect(totalAmount, cashAmount) {
        val cash = cashAmount.toDoubleOrNull() ?: 0.0
        val remaining = totalAmount - cash
        if (remaining >= 0) {
            wechatAmount = remaining
            debtAmount = 0.0
        } else {
            wechatAmount = 0.0
            debtAmount = -remaining
        }
    }

    // ============================================================
    // 保存记录（提取为函数，步骤4调用）
    // ============================================================
    fun saveRecord() {
        if (selectedEmployee != null) {
            scope.launch {
                val bottleDetails = mutableListOf<String>()
                var totalQty = 0
                var totalPrice = 0.0

                bottleItems.forEach { (type, item) ->
                    val qty = item.quantity.toIntOrNull()
                    val price = item.price.toDoubleOrNull()

                    if (type == BottleType.EXCHANGE) {
                        if (qty != null && qty > 0) {
                            totalQty += qty
                            val typeDetail = buildString {
                                append("${type.displayName}: ${qty}瓶")
                                if (item.yearSelections.isNotEmpty()) {
                                    append(" (${item.yearSelections.entries.joinToString(" ") { "${it.key}:${it.value}个" }})")
                                }
                            }
                            bottleDetails.add(typeDetail)
                        }
                    } else if (qty != null && qty > 0 && price != null) {
                        totalQty += qty
                        totalPrice += qty * price

                        val typeDetail = buildString {
                            val amount = qty * price
                            append("${type.displayName}: ${qty}瓶 × ¥${String.format("%.2f", price)} = ¥${String.format("%.2f", amount)}")
                            if (type == BottleType.RENTAL) {
                                if (item.yearSelections.isNotEmpty()) {
                                    append(" (${item.yearSelections.entries.joinToString(" ") { "${it.key}:${it.value}个" }})")
                                }
                            }
                            if (type == BottleType.RENTAL && item.customerName.isNotBlank()) {
                                append(" [对象: ${item.customerName}]")
                            }
                        }
                        bottleDetails.add(typeDetail)
                    }
                }

                if (bottleDetails.isNotEmpty()) {
                    val cash = cashAmount.toDoubleOrNull() ?: 0.0
                    val detailedNotes = buildString {
                        append(bottleDetails.joinToString(" | "))
                        if (notes.isNotBlank()) {
                            append(" | 备注: $notes")
                        }
                    }

                    val imagePath = photoFile?.absolutePath ?: ""

                    // 方案C：对瓶按年份拆成多条记录
                    val recordsToSave = mutableListOf<DeliveryRecord>()

                    // 1. 对瓶按年份拆分
                    val exchangeItem = bottleItems[BottleType.EXCHANGE]
                    if (exchangeItem != null) {
                        val exQty = exchangeItem.quantity.toIntOrNull() ?: 0
                        if (exQty > 0 && exchangeItem.yearSelections.isNotEmpty()) {
                            for ((year, yearQty) in exchangeItem.yearSelections) {
                                if (yearQty > 0) {
                                    recordsToSave.add(DeliveryRecord(
                                        employeeId = selectedEmployee!!.id,
                                        employeeName = selectedEmployee!!.name,
                                        bottleType = "EXCHANGE",
                                        quantity = yearQty,
                                        pricePerUnit = 0.0,
                                        totalAmount = 0.0,
                                        cashAmount = 0.0,
                                        wechatAmount = 0.0,
                                        debtAmount = 0.0,
                                        yearInfo = year,
                                        date = System.currentTimeMillis(),
                                        notes = "对瓶(${year}) x${yearQty}",
                                        imagePath = imagePath,
                                        exchangeStatus = "PENDING"
                                    ))
                                }
                            }
                        }
                    }

                    // 2. 非对瓶合并为一条
                    val nonExchangeDetails = bottleDetails.filter { !it.startsWith("对瓶:") }
                    if (nonExchangeDetails.isNotEmpty()) {
                        val nonExchangeQty = totalQty - (exchangeItem?.quantity?.toIntOrNull() ?: 0)
                        val cash = cashAmount.toDoubleOrNull() ?: 0.0
                        val nonExchangeNotes = buildString {
                            append(nonExchangeDetails.joinToString(" | "))
                            if (notes.isNotBlank()) {
                                append(" | 备注: $notes")
                            }
                        }
                        recordsToSave.add(DeliveryRecord(
                            employeeId = selectedEmployee!!.id,
                            employeeName = selectedEmployee!!.name,
                            bottleType = if (nonExchangeDetails.size == 1 && !selectedTypes.contains(BottleType.EXCHANGE))
                                selectedTypes.first().name else "MIXED",
                            quantity = nonExchangeQty,
                            pricePerUnit = if (nonExchangeQty > 0) totalPrice / nonExchangeQty else 0.0,
                            totalAmount = totalPrice,
                            cashAmount = cash,
                            wechatAmount = wechatAmount,
                            debtAmount = debtAmount,
                            yearInfo = "",
                            date = System.currentTimeMillis(),
                            notes = nonExchangeNotes,
                            imagePath = imagePath,
                            exchangeStatus = "NONE"
                        ))
                    }

                    // 3. 保存所有记录
                    android.util.Log.d("SYNC", "开始保存 ${recordsToSave.size} 条记录到云端...")
                    for (r in recordsToSave) {
                        deliveryRecordRepo.save(r)
                    }
                    android.util.Log.d("SYNC", "记录已保存")
                }

                // 重置
                selectedTypes = setOf()
                bottleItems = mapOf(BottleType.HEAVY to BottleTypeItem(
                    type = BottleType.HEAVY,
                    quantity = "",
                    price = bottleItems[BottleType.HEAVY]?.price ?: "0"
                ))
                cashAmount = ""
                notes = ""
                capturedImageUri = null
                photoFile = null
                currentStep = 1

                showSaveSnackbar = true
                navController.navigate("statistics")
            }
        }
    }

    // ============================================================
    // 主界面
    // ============================================================
    // Snackbar 提示
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(showSaveSnackbar) {
        if (showSaveSnackbar) {
            snackbarHostState.showSnackbar("✅ 保存成功")
            showSaveSnackbar = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 步骤指示器
            StepIndicator(currentStep = currentStep)

            Divider()

            // ============================================================
            // 步骤1: 选择员工
            // ============================================================
            if (currentStep == 1) {
                Text(
                    text = "第1步：选择配送员工",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                ExposedDropdownMenuBox(
                    expanded = expandedEmployee,
                    onExpandedChange = { expandedEmployee = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    OutlinedTextField(
                        value = selectedEmployee?.name ?: "请选择员工",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("选择员工", fontSize = 18.sp) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedEmployee) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedEmployee,
                        onDismissRequest = { expandedEmployee = false }
                    ) {
                        employees.forEach { employee ->
                            DropdownMenuItem(
                                text = { Text(employee.name, fontSize = 20.sp) },
                                onClick = {
                                    selectedEmployee = employee
                                    expandedEmployee = false
                                }
                            )
                        }
                    }
                }

                if (selectedEmployee != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "✓ 已选择：",
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = selectedEmployee!!.name,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // ============================================================
            // 步骤2: 填写瓶型
            // ============================================================
            if (currentStep == 2) {
                Text(
                    text = "第2步：填写瓶型数量",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // 重瓶（始终显示）
                HeavyBottleCard(
                    item = bottleItems[BottleType.HEAVY],
                    onQuantityChange = { newQty ->
                        bottleItems[BottleType.HEAVY]?.let { item ->
                            bottleItems = bottleItems + (BottleType.HEAVY to item.copy(quantity = newQty))
                        }
                    },
                    onPriceChange = { newPrice ->
                        bottleItems[BottleType.HEAVY]?.let { item ->
                            bottleItems = bottleItems + (BottleType.HEAVY to item.copy(price = newPrice))
                        }
                    }
                )

                // 其他类型选择
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "加选其他类型",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        listOf(BottleType.RENTAL, BottleType.EXCHANGE, BottleType.NEW, BottleType.SMALL).forEach { type ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = type.displayName,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Checkbox(
                                    checked = selectedTypes.contains(type),
                                    onCheckedChange = { checked ->
                                        selectedTypes = if (checked) {
                                            selectedTypes + type
                                        } else {
                                            selectedTypes - type
                                        }
                                    },
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }

                // 展开被选中的瓶型卡片
                selectedTypes.forEach { type ->
                    val item = bottleItems[type]
                    if (item != null) {
                        BottleTypeCard(
                            item = item,
                            availableYears = availableYears,
                            onQuantityChange = { newQty ->
                                bottleItems = bottleItems + (type to item.copy(quantity = newQty))
                            },
                            onPriceChange = { newPrice ->
                                bottleItems = bottleItems + (type to item.copy(price = newPrice))
                            },
                            onYearChange = { yearKey, qty ->
                                val newYearSelections = item.yearSelections.toMutableMap()
                                if (qty.isBlank() || qty.toIntOrNull() == 0) {
                                    newYearSelections.remove(yearKey)
                                } else {
                                    newYearSelections[yearKey] = qty
                                }
                                val totalQty = newYearSelections.values.sumOf { it.toIntOrNull() ?: 0 }
                                bottleItems = bottleItems + (type to item.copy(
                                    yearSelections = newYearSelections,
                                    quantity = totalQty.toString()
                                ))
                            },
                            onCustomerChange = { newCustomer ->
                                bottleItems = bottleItems + (type to item.copy(customerName = newCustomer))
                            }
                        )
                    }
                }
            }

            // ============================================================
            // 步骤3: 收款备注 + 拍照
            // ============================================================
            if (currentStep == 3) {
                Text(
                    text = "第3步：收款记录与备注",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                PaymentCard(
                    totalAmount = totalAmount,
                    cashAmount = cashAmount,
                    wechatAmount = wechatAmount,
                    debtAmount = debtAmount,
                    onCashAmountChange = { cashAmount = it }
                )

                // 备注
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("备注（选填）", fontSize = 18.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    minLines = 2,
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp)
                )

                // 拍照
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "📸 拍摄凭证（选填）",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // 只要有 photoFile 就进入图片区域（不检查 exists()，
                        // 因为相机可能还在写入，先显示加载中状态）
                        if (capturedImageUri != null && photoFile != null) {
                            // 异步加载图片：每次 imageRefreshTrigger 变化时重新读取文件
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
                                    .height(200.dp)
                            ) {
                                if (previewBitmap != null) {
                                    Image(
                                        bitmap = previewBitmap!!.asImageBitmap(),
                                        contentDescription = "已拍摄图片",
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
                                        photoFile?.delete()
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "拍照")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("拍摄图片", fontSize = 20.sp)
                            }
                        }
                    }
                }
            }

            // ============================================================
            // 步骤4: 确认预览
            // ============================================================
            if (currentStep == 4) {
                Text(
                    text = "第4步：确认信息无误后保存",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // 预览卡片
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "📋 配送信息预览",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(12.dp))

                        // 员工
                        Row {
                            Text("员工：", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Text(
                                selectedEmployee?.name ?: "未选择",
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        // 瓶型明细（模拟保存时的 building 逻辑）
                        val previewDetails = remember(bottleItems) {
                            val details = mutableListOf<String>()
                            bottleItems.forEach { (type, item) ->
                                val qty = item.quantity.toIntOrNull() ?: 0
                                val price = item.price.toDoubleOrNull() ?: 0.0
                                if (qty > 0) {
                                    if (type == BottleType.EXCHANGE) {
                                        details.add("${type.displayName}: ${qty}瓶")
                                    } else {
                                        details.add("${type.displayName}: ${qty}瓶 × ¥${String.format("%.2f", price)} = ¥${String.format("%.2f", qty * price)}")
                                    }
                                }
                            }
                            details
                        }

                        if (previewDetails.isEmpty()) {
                            Text("⚠ 未填写任何瓶型", fontSize = 18.sp, color = MaterialTheme.colorScheme.error)
                        } else {
                            previewDetails.forEach { detail ->
                                Text(detail, fontSize = 17.sp, modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(12.dp))

                        // 金额
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("总金额：", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "¥${String.format("%.2f", totalAmount)}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        val cash = cashAmount.toDoubleOrNull() ?: 0.0
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("现金支付：", fontSize = 17.sp)
                            Text("¥${String.format("%.2f", cash)}", fontSize = 17.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("微信支付：", fontSize = 17.sp)
                            Text("¥${String.format("%.2f", wechatAmount)}", fontSize = 17.sp)
                        }
                        if (debtAmount > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("欠款：", fontSize = 17.sp, color = MaterialTheme.colorScheme.error)
                                Text(
                                    "¥${String.format("%.2f", debtAmount)}",
                                    fontSize = 17.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        if (notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("备注：$notes", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ============================================================
            // 底部按钮：上一步 / 下一步 / 保存
            // ============================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 上一步按钮
                if (currentStep > 1) {
                    OutlinedButton(
                        onClick = { currentStep-- },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("上一步", fontSize = 18.sp)
                    }
                }

                // 下一步 / 保存按钮
                if (currentStep < 4) {
                    Button(
                        onClick = {
                            when (currentStep) {
                                1 -> if (selectedEmployee != null) currentStep++
                                2 -> currentStep++
                                3 -> currentStep++
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        enabled = when (currentStep) {
                            1 -> selectedEmployee != null
                            2, 3 -> true
                            else -> false
                        }
                    ) {
                        Text("下一步", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                } else {
                    Button(
                        onClick = { saveRecord() },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        enabled = selectedEmployee != null &&
                            bottleItems.values.any { it.quantity.toIntOrNull() != null && it.quantity.toInt() > 0 }
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("确认保存", fontSize = 18.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("需要相机权限", fontSize = 20.sp) },
            text = { Text("拍摄图片需要相机权限，请在设置中授予相机权限。", fontSize = 18.sp) },
            confirmButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("确定", fontSize = 18.sp)
                }
            }
        )
    }
}

// ============================================================
// 以下为子组件（原版保留，加大字号和按钮）
// ============================================================

@Composable
fun HeavyBottleCard(
    item: BottleTypeItem?,
    onQuantityChange: (String) -> Unit,
    onPriceChange: (String) -> Unit
) {
    if (item == null) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.type.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 22.sp
                )
                Text(
                    text = "¥${String.format("%.2f", (item.quantity.toDoubleOrNull() ?: 0.0) * (item.price.toDoubleOrNull() ?: 0.0))}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 22.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = item.quantity,
                    onValueChange = onQuantityChange,
                    label = { Text("数量", fontSize = 18.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp)
                )
                OutlinedTextField(
                    value = item.price,
                    onValueChange = onPriceChange,
                    label = { Text("单价 (元)", fontSize = 18.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp)
                )
            }
        }
    }
}

@Composable
fun BottleTypeCard(
    item: BottleTypeItem,
    availableYears: List<BottleYear>,
    onQuantityChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onYearChange: (String, String) -> Unit,
    onCustomerChange: (String) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.type.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 20.sp
                )
                if (item.type != BottleType.EXCHANGE) {
                    Text(
                        text = "¥${String.format("%.2f", (item.quantity.toDoubleOrNull() ?: 0.0) * (item.price.toDoubleOrNull() ?: 0.0))}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = 20.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (item.type == BottleType.RENTAL) {
                OutlinedTextField(
                    value = item.customerName,
                    onValueChange = onCustomerChange,
                    label = { Text("对象 (客户名称)", fontSize = 18.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (item.type == BottleType.RENTAL || item.type == BottleType.EXCHANGE) {
                Text(
                    text = "选择年份和数量：",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                availableYears.chunked(2).forEach { rowYears ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowYears.forEach { year ->
                            val yearKey = "${year.year}${year.type}"
                            val currentQty = item.yearSelections[yearKey]?.toIntOrNull() ?: 0

                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = yearKey,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 20.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        FilledTonalButton(
                                            onClick = {
                                                if (currentQty > 0) {
                                                    onYearChange(yearKey, (currentQty - 1).toString())
                                                }
                                            },
                                            modifier = Modifier.size(48.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("-", fontSize = 24.sp)
                                        }
                                        Text(
                                            text = currentQty.toString(),
                                            style = MaterialTheme.typography.headlineMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 28.sp,
                                            modifier = Modifier.padding(horizontal = 20.dp)
                                        )
                                        FilledTonalButton(
                                            onClick = {
                                                onYearChange(yearKey, (currentQty + 1).toString())
                                            },
                                            modifier = Modifier.size(48.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("+", fontSize = 24.sp)
                                        }
                                    }
                                }
                            }
                        }
                        if (rowYears.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                val totalYearQty = item.yearSelections.values.sumOf { it.toIntOrNull() ?: 0 }
                Text(
                    text = "年份总数: $totalYearQty 个",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = item.quantity,
                        onValueChange = {},
                        label = { Text("总数量", fontSize = 18.sp) },
                        modifier = Modifier.weight(1f),
                        readOnly = true,
                        enabled = false,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp)
                    )
                    if (item.type != BottleType.EXCHANGE) {
                        OutlinedTextField(
                            value = item.price,
                            onValueChange = onPriceChange,
                            label = { Text("单价 (元)", fontSize = 18.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = item.quantity,
                        onValueChange = onQuantityChange,
                        label = { Text("数量", fontSize = 18.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp)
                    )
                    OutlinedTextField(
                        value = item.price,
                        onValueChange = onPriceChange,
                        label = { Text("单价 (元)", fontSize = 18.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp)
                    )
                }
            }
        }
    }
}

@Composable
fun PaymentCard(
    totalAmount: Double,
    cashAmount: String,
    wechatAmount: Double,
    debtAmount: Double,
    onCashAmountChange: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "总金额：",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "¥ ${String.format("%.2f", totalAmount)}",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "收款记录（仅记录，非实际支付）",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = cashAmount,
                onValueChange = onCashAmountChange,
                label = { Text("现金 (元)", fontSize = 18.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 24.sp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("微信：", fontSize = 18.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "¥ ${String.format("%.2f", wechatAmount)}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (wechatAmount > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            if (debtAmount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("欠款：", fontSize = 18.sp, color = MaterialTheme.colorScheme.error)
                    Text(
                        "¥ ${String.format("%.2f", debtAmount)}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
