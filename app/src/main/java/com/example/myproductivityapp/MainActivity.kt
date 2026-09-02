package com.example.myproductivityapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.myproductivityapp.data.AppDatabase
import com.example.myproductivityapp.data.local.DeviceIdentity
import com.example.myproductivityapp.data.local.DeviceIdentityManager
import com.example.myproductivityapp.data.local.DeviceRole
import com.example.myproductivityapp.data.local.LocalServerBootstrap
import com.example.myproductivityapp.data.local.ServerConfigManager
import com.example.myproductivityapp.data.remote.LocalServerClient
import com.example.myproductivityapp.data.remote.NoopRemoteDataClient
import com.example.myproductivityapp.data.remote.RemoteDataClient
import com.example.myproductivityapp.data.repository.*
import com.example.myproductivityapp.ui.screens.*
import com.example.myproductivityapp.ui.theme.MyProductivityAppTheme

class MainActivity : ComponentActivity() {
    companion object {
        // 保留旧名字，减少 V2 过渡期对旧页面的改动；实际已经是本地服务器客户端。
        lateinit var cloudClient: RemoteDataClient
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyProductivityAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    V2AppRoot()
                }
            }
        }
    }
}

@Composable
private fun V2AppRoot() {
    val context = LocalContext.current
    val serverManager = remember { ServerConfigManager(context) }
    var serverConfig by remember { mutableStateOf(serverManager.load()) }
    var showSetup by remember { mutableStateOf(false) }

    val config = serverConfig
    // 服务器配置页（可跳过）：有配置=修改，无配置=添加。不再是进 App 的强制关卡。
    if (showSetup) {
        V2ServerSetupScreen(
            initial = config,
            onConnected = { connected ->
                serverManager.save(connected)
                serverConfig = connected
                showSetup = false
            },
            onSkip = { showSetup = false }
        )
        return
    }

    // 有服务器配置 → 真客户端；没有 → 空实现（纯本地模式，断网可用）
    val client = remember(config) {
        if (config != null) LocalServerClient(config.baseUrl, config.apiKey)
        else NoopRemoteDataClient()
    }
    // 旧页面/旧 ViewModel 仍从这个兼容入口拿 client；必须在子页面组合前就赋值。
    MainActivity.cloudClient = client

    V2LoginGate(
        client = client,
        hasServer = config != null,
        onChangeServer = {
            serverManager.clear()
            serverConfig = null
            showSetup = true
        }
    )
}

@Composable
private fun V2LoginGate(
    client: RemoteDataClient,
    hasServer: Boolean,
    onChangeServer: () -> Unit
) {
    val context = LocalContext.current
    val manager = remember { DeviceIdentityManager(context) }
    var identity by remember { mutableStateOf(manager.load()) }
    var showNotEmployee by remember { mutableStateOf(false) }

    // 身份校验：DRIVER 的稳定 remoteId 缺失时，若能匹配到员工则补绑（按手机号）；
    // 若配了服务器且在线、员工表里查无此人 → 弹提示"不是本站员工"（本地功能不受影响）。
    LaunchedEffect(identity, hasServer, client) {
        val current = identity ?: return@LaunchedEffect
        if (current.role != DeviceRole.DRIVER) return@LaunchedEffect
        if (current.employeeRemoteId.isNotBlank()) return@LaunchedEffect

        val db = AppDatabase.getDatabase(context)
        suspend fun findLocal(): com.example.myproductivityapp.data.model.Employee? =
            if (current.phone.isNotBlank()) db.employeeDao().getEmployeeByPhone(current.phone)
            else db.employeeDao().getAllEmployeesOnce()
                .filter { it.name == current.employeeName }
                .singleOrNull()

        var matched = findLocal()
        // 配了服务器且在线 → 先同步一次员工表再判断，保证是服务器/营业员建过的最新名单
        val serverOnline = hasServer && client.health()
        if (matched == null && serverOnline) {
            runCatching { EmployeeRepository(db.employeeDao(), client).syncFromCloud() }
            matched = findLocal()
        }

        if (matched != null) {
            // 员工存在（营业员创建过）→ 补绑本地身份；只有真正变化才写入，避免死循环
            val changed = current.employeeId != matched.id || current.employeeRemoteId != matched.firestoreId
            if (changed) {
                val fixed = current.copy(
                    employeeId = matched.id,
                    employeeRemoteId = matched.firestoreId
                )
                manager.save(fixed)
                identity = fixed
            }
        } else if (serverOnline) {
            // 服务器在线但名单里没有这个手机号 → 明确的非员工提示
            showNotEmployee = true
        }
    }

    if (showNotEmployee) {
        AlertDialog(
            onDismissRequest = { showNotEmployee = false },
            title = { Text("你不是本站员工", fontSize = 22.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "手机号 ${identity?.phone.orEmpty()} 不在员工名单里。\n\n" +
                        "送气工账号需要营业员/站长在设置里添加员工后才能使用。\n\n" +
                        "本机本地功能仍可使用，但看不到服务器的派单和记录。",
                    fontSize = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showNotEmployee = false }) { Text("知道了", fontSize = 18.sp) }
            }
        )
    }

    val current = identity
    if (current == null) {
        V2LoginScreen { selected ->
            manager.save(selected)
            identity = selected
        }
    } else {
        V2MainScreen(
            client = client,
            identity = current,
            hasServer = hasServer,
            onChangeIdentity = {
                manager.clear()
                identity = null
            },
            onChangeServer = onChangeServer
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V2MainScreen(
    client: RemoteDataClient,
    identity: DeviceIdentity,
    hasServer: Boolean,
    onChangeIdentity: () -> Unit,
    onChangeServer: () -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "record"

    LaunchedEffect(client, hasServer) {
        val db = AppDatabase.getDatabase(context)
        // 旧 CloudBase 数据迁移到新站服务器：只在配置了服务器时后台跑，失败不卡入口（之后会自动补推）
        if (hasServer) {
            runCatching { LocalServerBootstrap(context).runIfNeeded(db, client) }
        }
        val employeeRepo = EmployeeRepository(db.employeeDao(), client)
        val deliveryRecordRepo = DeliveryRecordRepository(db.deliveryRecordDao(), client)
        val priceConfigRepo = PriceConfigRepository(db.priceConfigDao(), client)
        val bottleYearRepo = BottleYearRepository(db.bottleYearDao(), client)
        val deliveryTaskRepo = DeliveryTaskRepository(db.deliveryTaskDao(), client)

        val sync = SyncManager(
            employeeRepo = employeeRepo,
            deliveryRecordRepo = deliveryRecordRepo,
            priceConfigRepo = priceConfigRepo,
            bottleYearRepo = bottleYearRepo,
            deliveryTaskRepo = deliveryTaskRepo
        )
        sync.initialSync()
        sync.startWatch()
        sync.startFlush()
    }

    val title = when (currentRoute) {
        "record" -> "久隆站助手"
        "tasks" -> "待办"
        "ledger" -> "统计"
        "settings" -> "设置"
        else -> "久隆站助手"
    }

    val isDriver = identity.role == DeviceRole.DRIVER

    // 路由保护：送气工禁止进入记账/设置页（无论旧导航状态还是其他入口）
    LaunchedEffect(currentRoute, isDriver) {
        if (isDriver && (currentRoute == "settings" || currentRoute == "record")) {
            navController.navigate("tasks") {
                popUpTo("tasks") { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (currentRoute == "settings") {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    if (currentRoute == "settings") {
                        TextButton(onClick = onChangeIdentity) { Text("退出登录") }
                        TextButton(onClick = onChangeServer) { Text("服务器") }
                    } else if (!isDriver) {
                        // 只有营业员/站长能看到设置入口
                        IconButton(onClick = { navController.navigate("settings") }) {
                            Icon(Icons.Default.Settings, contentDescription = "设置")
                        }
                    } else {
                        // 送气工：待办/统计页也能配置服务器（登录后才能连，断网也能用）
                        TextButton(onClick = onChangeServer) { Text("服务器") }
                    }
                }
            )
        },
        bottomBar = {
            if (currentRoute != "settings") {
                NavigationBar {
                    if (!isDriver) {
                        NavigationBarItem(
                            selected = currentRoute == "record",
                            onClick = {
                                navController.navigate("record") {
                                    popUpTo("record") { inclusive = false }
                                    launchSingleTop = true
                                }
                            },
                            icon = { Icon(Icons.Default.EditNote, contentDescription = "记账") },
                            label = { Text("记账") }
                        )
                    }
                    NavigationBarItem(
                        selected = currentRoute == "tasks",
                        onClick = { navController.navigate("tasks") { launchSingleTop = true } },
                        icon = { Icon(Icons.Default.Assignment, contentDescription = "待办") },
                        label = { Text("待办") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == "ledger",
                        onClick = { navController.navigate("ledger") { launchSingleTop = true } },
                        icon = { Icon(Icons.Default.MenuBook, contentDescription = "统计") },
                        label = { Text("统计") }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (isDriver) "tasks" else "record",
            modifier = Modifier.padding(padding)
        ) {
            composable("record") { V2RecordScreen() }
            composable("tasks") { V2TasksScreen(identity) }
            composable("ledger") { V2LedgerScreen(identity) }
            composable("settings") { SettingsScreen(isAdmin = !isDriver) }
        }
    }
}
