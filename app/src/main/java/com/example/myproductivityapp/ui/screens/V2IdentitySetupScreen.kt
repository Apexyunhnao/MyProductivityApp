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
    onConfigured: (DeviceIdentity) -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val repo = remember { EmployeeRepository(db.employeeDao(), client) }
    var employees by remember { mutableStateOf<List<Employee>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

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
            onClick = { onConfigured(DeviceIdentity(DeviceRole.OFFICE, null, "营业员")) },
            modifier = Modifier.fillMaxWidth().height(62.dp)
        ) {
            Text("营业员", fontSize = 22.sp)
        }

        Button(
            onClick = { onConfigured(DeviceIdentity(DeviceRole.MANAGER, null, "站长")) },
            modifier = Modifier.fillMaxWidth().height(62.dp)
        ) {
            Text("站长", fontSize = 22.sp)
        }

        HorizontalDivider()
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
                            onConfigured(
                                DeviceIdentity(
                                    role = DeviceRole.DRIVER,
                                    employeeId = employee.id,
                                    employeeName = employee.name
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(58.dp)
                    ) {
                        Text(employee.name, fontSize = 21.sp)
                    }
                }
            }
        }
    }
}
