package com.example.myproductivityapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.myproductivityapp.auth.AuthManager
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
        is AuthState.LoggedOut -> {
            LoginScreen(viewModel = viewModel)
        }
        is AuthState.LoggedIn -> {
            MainScreen(client = client)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(client: CloudBaseClient) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val authManager = remember { AuthManager(client) }

    // 同步管理器
    LaunchedEffect(Unit) {
        val db = AppDatabase.getDatabase(context)
        val employeeRepo = EmployeeRepository(db.employeeDao(), client)
        val deliveryRecordRepo = DeliveryRecordRepository(db.deliveryRecordDao(), client)
        val priceConfigRepo = PriceConfigRepository(db.priceConfigDao(), client)
        val bottleYearRepo = BottleYearRepository(db.bottleYearDao(), client)

        val sync = SyncManager(employeeRepo, deliveryRecordRepo, priceConfigRepo, bottleYearRepo)
        sync.initialSync()
        sync.startWatch()
        sync.startFlush()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(when (currentRoute) {
                    "add_record" -> "配送记录"; "statistics" -> "统计报表"
                    "settings" -> "系统设置"; else -> "煤气站配送管理"
                }) },
                actions = {
                    IconButton(onClick = { authManager.signOut() }) {
                        Icon(Icons.Default.ExitToApp, "退出")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Add, "记录") }, label = { Text("记录") },
                    selected = currentRoute == "add_record",
                    onClick = { navController.navigate("add_record") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.DateRange, "统计") }, label = { Text("统计") },
                    selected = currentRoute == "statistics",
                    onClick = { navController.navigate("statistics") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, "设置") }, label = { Text("设置") },
                    selected = currentRoute == "settings",
                    onClick = { navController.navigate("settings") }
                )
            }
        }
    ) { padding ->
        NavHost(navController, startDestination = "add_record", modifier = Modifier.padding(padding)) {
            composable("add_record") { AddRecordScreen(navController = navController) }
            composable("statistics") { StatisticsScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
