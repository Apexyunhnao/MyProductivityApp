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
import com.example.myproductivityapp.auth.AuthState
import com.example.myproductivityapp.auth.AuthViewModel
import com.example.myproductivityapp.data.AppDatabase
import com.example.myproductivityapp.data.cloudbase.CloudBaseClient
import com.example.myproductivityapp.data.repository.*
import com.example.myproductivityapp.ui.screens.*
import com.example.myproductivityapp.ui.theme.MyProductivityAppTheme

class MainActivity : ComponentActivity() {
    companion object {
        lateinit var cloudClient: CloudBaseClient
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cloudClient = CloudBaseClient("gas-station-d2gq3uauq82a3cc6d")

        setContent {
            MyProductivityAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppEntryPoint()
                }
            }
        }
    }
}

@Composable
fun AppEntryPoint() {
    val client = MainActivity.cloudClient
    val viewModel = remember { AuthViewModel(client) }
    val authState by viewModel.authState.collectAsState()

    when (authState) {
        is AuthState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在连接云端...")
                }
            }
        }
        is AuthState.LoggedOut -> LoginScreen(viewModel = viewModel)
        is AuthState.LoggedIn -> V2MainScreen(client = client)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V2MainScreen(client: CloudBaseClient) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "record"

    LaunchedEffect(Unit) {
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
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
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
            composable("tasks") { V2TasksScreen() }
            composable("ledger") { V2LedgerScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
