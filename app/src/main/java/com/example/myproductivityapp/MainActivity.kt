package com.example.myproductivityapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.myproductivityapp.data.AppDatabase
import com.example.myproductivityapp.data.local.DeviceIdentity
import com.example.myproductivityapp.data.local.DeviceIdentityManager
import com.example.myproductivityapp.data.local.ServerConfig
import com.example.myproductivityapp.data.local.ServerConfigManager
import com.example.myproductivityapp.data.remote.LocalServerClient
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

    val config = serverConfig
    if (config == null) {
        V2ServerSetupScreen { connected ->
            serverManager.save(connected)
            serverConfig = connected
        }
        return
    }

    val client = remember(config.baseUrl, config.apiKey) {
        LocalServerClient(config.baseUrl, config.apiKey)
    }
    SideEffect {
        MainActivity.cloudClient = client
    }

    V2IdentityGate(
        client = client,
        onChangeServer = {
            serverManager.clear()
            serverConfig = null
        }
    )
}

@Composable
private fun V2IdentityGate(
    client: RemoteDataClient,
    onChangeServer: () -> Unit
) {
    val context = LocalContext.current
    val manager = remember { DeviceIdentityManager(context) }
    var identity by remember { mutableStateOf(manager.load()) }

    val current = identity
    if (current == null) {
        V2IdentitySetupScreen(client = client) { selected ->
            manager.save(selected)
            identity = selected
        }
    } else {
        V2MainScreen(
            client = client,
            identity = current,
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
    onChangeIdentity: () -> Unit,
    onChangeServer: () -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "record"

    LaunchedEffect(client) {
        val db = AppDatabase.getDatabase(context)
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
        "ledger" -> "账本"
        "settings" -> "设置"
        else -> "久隆站助手"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                actions = {
                    if (currentRoute == "settings") {
                        TextButton(onClick = onChangeIdentity) { Text("换身份") }
                        TextButton(onClick = onChangeServer) { Text("服务器") }
                    } else {
                        IconButton(onClick = { navController.navigate("settings") }) {
                            Icon(Icons.Default.Settings, contentDescription = "设置")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (currentRoute != "settings") {
                NavigationBar {
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
                    NavigationBarItem(
                        selected = currentRoute == "tasks",
                        onClick = { navController.navigate("tasks") { launchSingleTop = true } },
                        icon = { Icon(Icons.Default.Assignment, contentDescription = "待办") },
                        label = { Text("待办") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == "ledger",
                        onClick = { navController.navigate("ledger") { launchSingleTop = true } },
                        icon = { Icon(Icons.Default.MenuBook, contentDescription = "账本") },
                        label = { Text("账本") }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "record",
            modifier = Modifier.padding(padding)
        ) {
            composable("record") { V2RecordScreen() }
            composable("tasks") { V2TasksScreen(identity) }
            composable("ledger") { V2LedgerScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
