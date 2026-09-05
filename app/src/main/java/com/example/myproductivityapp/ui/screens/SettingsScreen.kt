package com.example.myproductivityapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myproductivityapp.data.AppDatabase
import com.example.myproductivityapp.data.model.BottleType
import com.example.myproductivityapp.data.model.BottleTypes
import com.example.myproductivityapp.data.model.BottleYear
import com.example.myproductivityapp.data.model.Employee
import com.example.myproductivityapp.data.model.PriceConfig
import com.example.myproductivityapp.viewmodel.EmployeeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(isAdmin: Boolean = true) {
    // 权限加固：送气工即使被路由误放进来也看不到任何管理内容。
    // 营业员/站长 isAdmin=true，不受影响。
    if (!isAdmin) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("没有权限", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("只有营业员/站长可以管理员工、价格和年份。", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val viewModel: EmployeeViewModel = viewModel()
    val scope = rememberCoroutineScope()

    // Repositories for price config and bottle years
    val client = com.example.myproductivityapp.MainActivity.cloudClient
    val priceConfigRepo = remember { com.example.myproductivityapp.data.repository.PriceConfigRepository(database.priceConfigDao(), client) }
    val bottleYearRepo = remember { com.example.myproductivityapp.data.repository.BottleYearRepository(database.bottleYearDao(), client) }

    var selectedTab by remember { mutableStateOf(0) }
    val employees by viewModel.employees.collectAsState()
    var priceConfigs by remember { mutableStateOf<List<PriceConfig>>(emptyList()) }
    var bottleYears by remember { mutableStateOf<List<BottleYear>>(emptyList()) }

    var showEmployeeDialog by remember { mutableStateOf(false) }
    var editingEmployee by remember { mutableStateOf<Employee?>(null) }
    var employeeToDelete by remember { mutableStateOf<Employee?>(null) }
    var editingPriceType by remember { mutableStateOf<String?>(null) }
    var showPriceDialog by remember { mutableStateOf(false) }
    var showAddTypeDialog by remember { mutableStateOf(false) }
    var deletingPrice by remember { mutableStateOf<PriceConfig?>(null) }
    var showYearDialog by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        priceConfigRepo.observeAll().collect { prices ->
            priceConfigs = prices
        }
    }

    LaunchedEffect(Unit) {
        bottleYearRepo.observeAll().collect { years ->
            bottleYears = years
        }
    }

    // 首次启动：给内置 5 种瓶型补默认价格记录（只补一次；之后用户删掉的内置类型不会被自动加回来）
    LaunchedEffect(Unit) {
        val prefs = context.applicationContext.getSharedPreferences("price_init", 0)
        if (prefs.getBoolean("seeded", false)) return@LaunchedEffect
        scope.launch {
            BottleType.values().forEach { type ->
                if (priceConfigRepo.getByType(type.name) == null) {
                    priceConfigRepo.save(
                        PriceConfig(
                            bottleType = type.name,
                            price = 0,
                            lastUpdated = System.currentTimeMillis()
                        )
                    )
                }
            }
            prefs.edit().putBoolean("seeded", true).apply()
        }
    }

    // 首次启动：删除重复年份，补默认年份
    LaunchedEffect(initialized) {
        if (initialized) return@LaunchedEffect
        kotlinx.coroutines.delay(2000)
        scope.launch {
            // 去重：同一 year+type 只保留有 firestoreId 的
            val grouped = bottleYears.groupBy { "${it.year}${it.type}" }
            val toDelete = mutableListOf<BottleYear>()
            for ((_, items) in grouped) {
                if (items.size > 1) {
                    // 优先保留有 firestoreId 的，其他都删
                    val keep = items.firstOrNull { it.firestoreId.isNotBlank() } ?: items.first()
                    items.forEach { if (it.id != keep.id) toDelete.add(it) }
                }
            }
            toDelete.forEach { bottleYearRepo.delete(it) }

        }
        initialized = true
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("员工管理") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("价格设置") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("年份管理") }
            )
        }

        when (selectedTab) {
            0 -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (employees.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "暂无员工信息",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "点击右下角按钮添加员工",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(employees) { employee ->
                                EmployeeCard(
                                    employee = employee,
                                    onEdit = {
                                        editingEmployee = employee
                                        showEmployeeDialog = true
                                    },
                                    onDelete = { employeeToDelete = employee }
                                )
                            }
                        }
                    }

                    FloatingActionButton(
                        onClick = {
                            editingEmployee = null
                            showEmployeeDialog = true
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "添加员工")
                    }
                }
            }
            1 -> {
                // 排序：内置 5 种按固定顺序在前，自定义类型在后
                val sortedPrices = remember(priceConfigs) {
                    priceConfigs.sortedBy { cfg ->
                        val idx = BottleType.values().indexOfFirst { it.name == cfg.bottleType }
                        if (idx >= 0) idx else 1000
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "提示",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "设置各类型煤气罐的默认价格，添加配送记录时会自动填充该价格。右下角 + 可以添加自定义瓶型（如中瓶）。",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(sortedPrices) { config ->
                            PriceCard(
                                priceConfig = config,
                                onEdit = {
                                    editingPriceType = config.bottleType
                                    showPriceDialog = true
                                },
                                onDelete = { deletingPrice = config }
                            )
                        }
                    }
                    FloatingActionButton(
                        onClick = { showAddTypeDialog = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "添加瓶型")
                    }
                }
            }
            2 -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "提示",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "管理租瓶和换瓶可选的年份类型，例如：22厂、22检、23厂、23检等。",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(bottleYears) { year ->
                            YearCard(
                                bottleYear = year,
                                onDelete = {
                                    scope.launch {
                                        bottleYearRepo.delete(year)
                                    }
                                }
                            )
                        }
                    }

                    FloatingActionButton(
                        onClick = { showYearDialog = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "添加年份")
                    }
                }
            }
        }
    }

    if (employeeToDelete != null) {
        AlertDialog(
            onDismissRequest = { employeeToDelete = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除员工「${employeeToDelete!!.name}」吗？该员工的所有配送记录将被保留。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteEmployee(employeeToDelete!!)
                        employeeToDelete = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { employeeToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (showEmployeeDialog) {
        AddEmployeeDialog(
            employee = editingEmployee,
            onDismiss = {
                showEmployeeDialog = false
                editingEmployee = null
            },
            onConfirm = { name, phone ->
                if (editingEmployee != null) {
                    viewModel.updateEmployee(
                        editingEmployee!!.copy(
                            name = name,
                            phoneNumber = phone
                        )
                    )
                } else {
                    viewModel.addEmployee(name, phone)
                }
                showEmployeeDialog = false
                editingEmployee = null
            }
        )
    }

    if (showPriceDialog && editingPriceType != null) {
        val currentConfig = priceConfigs.find { it.bottleType == editingPriceType }
        EditPriceDialog(
            bottleType = editingPriceType!!,
            currentPrice = currentConfig?.price ?: 0,
            onDismiss = {
                showPriceDialog = false
                editingPriceType = null
            },
            onConfirm = { newPrice ->
                scope.launch {
                    // 必须带上现有 firestoreId，否则 REPLACE 会把远端 ID 清空、
                    // 保存走 add() 在服务器新增重复记录，下次同步又拉回旧价格（修改无效的根因）
                    val current = priceConfigs.find { it.bottleType == editingPriceType }
                    priceConfigRepo.save(
                        PriceConfig(
                            bottleType = editingPriceType!!,
                            price = newPrice,
                            lastUpdated = System.currentTimeMillis(),
                            firestoreId = current?.firestoreId ?: "",
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    showPriceDialog = false
                    editingPriceType = null
                }
            }
        )
    }

    if (showYearDialog) {
        AddYearDialog(
            onDismiss = { showYearDialog = false },
            onConfirm = { year, type ->
                if (bottleYears.none { it.year == year && it.type == type }) {
                    scope.launch {
                        bottleYearRepo.save(BottleYear(year = year, type = type))
                        showYearDialog = false
                    }
                }
            }
        )
    }

    if (showAddTypeDialog) {
        AddBottleTypeDialog(
            existingTypes = priceConfigs.map { BottleTypes.displayName(it.bottleType) },
            onDismiss = { showAddTypeDialog = false },
            onConfirm = { name, price ->
                scope.launch {
                    priceConfigRepo.save(
                        PriceConfig(
                            bottleType = name,
                            price = price,
                            lastUpdated = System.currentTimeMillis()
                        )
                    )
                    showAddTypeDialog = false
                }
            }
        )
    }

    deletingPrice?.let { price ->
        AlertDialog(
            onDismissRequest = { deletingPrice = null },
            title = { Text("删除瓶型") },
            text = {
                Text("确定删除「${price.bottleType}」吗？历史记录不会丢，只是以后记账不能再选这个类型。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            priceConfigRepo.delete(price)
                            deletingPrice = null
                        }
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingPrice = null }) { Text("取消") }
            }
        )
    }
}

@Composable
fun EmployeeCard(
    employee: Employee,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = employee.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "电话: ${employee.phoneNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "编辑",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun AddEmployeeDialog(
    employee: Employee?,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(employee?.name ?: "") }
    var phone by remember { mutableStateOf(employee?.phoneNumber ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (employee == null) "添加员工" else "编辑员工") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("员工姓名") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("联系电话") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name, phone)
                    }
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

@Composable
fun PriceCard(
    priceConfig: PriceConfig,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val bottleType = BottleType.values().find { it.name == priceConfig.bottleType }
    val displayName = bottleType?.displayName ?: priceConfig.bottleType

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "¥ ${priceConfig.price} / 瓶",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Button(onClick = onEdit) {
                Text("修改")
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除类型",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun AddBottleTypeDialog(
    existingTypes: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加瓶型") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("类型名（例如：中瓶）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("价格（元/瓶）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cleanName = name.trim()
                    val price = priceText.toIntOrNull()
                    when {
                        cleanName.isBlank() -> error = "类型名不能为空"
                        cleanName in existingTypes -> error = "这个类型已经存在"
                        price == null || price < 0 -> error = "价格要填数字"
                        else -> onConfirm(cleanName, price)
                    }
                }
            ) { Text("确认") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun EditPriceDialog(
    bottleType: String,
    currentPrice: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    // remember 必须以 currentPrice/bottleType 为 key：否则 dialog 复用时残留上次输入值（表现为编辑值变 0/旧值）
    var price by remember(bottleType, currentPrice) { mutableStateOf(currentPrice.toString()) }
    val type = BottleType.values().find { it.name == bottleType }
    val displayName = type?.displayName ?: bottleType

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改${displayName}价格") },
        text = {
            Column {
                Text(
                    text = "当前价格: ¥$currentPrice",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("新价格 (元)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newPrice = price.toIntOrNull()
                    if (newPrice != null && newPrice >= 0) {
                        onConfirm(newPrice)
                    }
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

@Composable
fun YearCard(
    bottleYear: BottleYear,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${bottleYear.year}${bottleYear.type}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddYearDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var year by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("厂") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加年份") },
        text = {
            Column {
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it },
                    label = { Text("年份 (例如: 22, 23, 24)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "类型",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedType == "厂",
                        onClick = { selectedType = "厂" },
                        label = { Text("厂") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedType == "检",
                        onClick = { selectedType = "检" },
                        label = { Text("检") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (year.isNotBlank()) {
                        onConfirm(year, selectedType)
                    }
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
