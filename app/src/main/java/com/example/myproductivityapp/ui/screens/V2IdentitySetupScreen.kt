package com.example.myproductivityapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myproductivityapp.data.AppDatabase
import com.example.myproductivityapp.data.local.DeviceIdentity
import com.example.myproductivityapp.data.local.DeviceRole
import com.example.myproductivityapp.data.model.Employee
import com.example.myproductivityapp.data.remote.RemoteDataClient
import com.example.myproductivityapp.data.repository.EmployeeRepository

@Composable
fun V2IdentitySetupScreen(
    client: RemoteDataClient,
    needConfirm: Boolean = false,
    onConfigured: (DeviceIdentity) -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val repo = remember { EmployeeRepository(db.employeeDao(), client) }
    var employees by remember { mutableStateOf<List<Employee>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    // 待确认的送气工：点击名字先弹确认，确认后才锁定为送气工身份
    var pendingDriver by remember { mutableStateOf<Employee?>(null) }

    LaunchedEffect(Unit) {
        runCatching { repo.syncFromCloud() }
        loading = false
    }
    LaunchedEffect(Unit) {
        repo.observeAll().collect { employees = it }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("这台手机是谁在用？", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("只选一次，以后自动记住。", fontSize = 18.sp)

        Button(
            onClick = {
                onConfigured(
                    DeviceIdentity(
                        role = DeviceRole.OFFICE,
                        employeeName = "营业员"
                    )
                )
            },
            modifier = Modifier.fillMaxWidth().height(62.dp)
        ) {
            Text("营业员", fontSize = 22.sp)
        }

        Button(
            onClick = {
                onConfigured(
                    DeviceIdentity(
                        role = DeviceRole.MANAGER,
                        employeeName = "站长"
                    )
                )
            },
            modifier = Modifier.fillMaxWidth().height(62.dp)
        ) {
            Text("站长", fontSize = 22.sp)
        }

        Divider()
        Text("送气员：点自己的名字", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        if (loading && employees.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text("正在读取员工…")
            }
        } else if (employees.isEmpty()) {
            Text("还没有员工。先用营业员身份进入设置添加员工，再回来绑定送气员手机。")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(employees, key = { it.id }) { employee ->
                    OutlinedButton(
                        onClick = {
                            if (needConfirm) {
                                pendingDriver = employee
                            } else {
                                // 首次设置直接绑定，不弹确认
                                onConfigured(
                                    DeviceIdentity(
                                        role = DeviceRole.DRIVER,
                                        employeeId = employee.id,
                                        employeeRemoteId = employee.firestoreId,
                                        employeeName = employee.name
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(58.dp)
                    ) {
                        Text(employee.name, fontSize = 21.sp)
                    }
                }
            }
        }
    }

    pendingDriver?.let { driver ->
        AlertDialog(
            onDismissRequest = { pendingDriver = null },
            title = { Text("确认选送气工", fontSize = 22.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "选了送气工后，这台手机就锁定为 ${driver.name}，之后不能自己切换身份（送气工只能看待办和统计）。确定要选吗？",
                    fontSize = 19.sp
                )
            },
            confirmButton = {
                Button(onClick = {
                    onConfigured(
                        DeviceIdentity(
                            role = DeviceRole.DRIVER,
                            employeeId = driver.id,
                            employeeRemoteId = driver.firestoreId,
                            employeeName = driver.name
                        )
                    )
                    pendingDriver = null
                }) { Text("确定", fontSize = 19.sp) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDriver = null }) { Text("取消") }
            }
        )
    }
}
