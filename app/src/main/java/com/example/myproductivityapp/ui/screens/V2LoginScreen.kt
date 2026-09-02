package com.example.myproductivityapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myproductivityapp.data.AppDatabase
import com.example.myproductivityapp.data.local.DeviceIdentity
import com.example.myproductivityapp.data.local.DeviceRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * V2 登录：手机号 + 密码。
 * - 站长/营业员是固定账号（写死，不增不减），密码 = "a" + 手机号
 * - 送气工账号由营业员/站长在设置里添加员工（名字+手机号）生成，密码同样 = "a" + 手机号
 * 纯本地校验，断网也能登录。
 */
@Composable
fun V2LoginScreen(
    onLoggedIn: (DeviceIdentity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    // 固定管理员账号：手机号 -> 角色名
    val adminAccounts = mapOf(
        "13877786438" to DeviceRole.MANAGER, // 站长
        "18070781782" to DeviceRole.OFFICE   // 营业员
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(22.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("久隆站助手", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("手机号 + 密码登录", fontSize = 16.sp)
        Spacer(Modifier.height(26.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("手机号") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 16.sp)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (checking) return@Button
                val cleanPhone = phone.trim()
                val cleanPwd = password.trim()
                if (cleanPhone.isBlank() || cleanPwd.isBlank()) {
                    error = "手机号和密码都要填"
                    return@Button
                }
                checking = true
                error = null
                scope.launch {
                    val identity = withContext(Dispatchers.IO) {
                        when {
                            // 站长/营业员固定账号：密码 = a + 手机号
                            adminAccounts.containsKey(cleanPhone) && cleanPwd == "a$cleanPhone" -> DeviceIdentity(
                                role = adminAccounts.getValue(cleanPhone),
                                employeeName = if (adminAccounts.getValue(cleanPhone) == DeviceRole.MANAGER) "站长" else "营业员",
                                phone = cleanPhone
                            )
                            // 送气工：密码统一 abc123
                            cleanPwd == "abc123" -> {
                                val emp = AppDatabase.getDatabase(context)
                                    .employeeDao().getEmployeeByPhone(cleanPhone)
                                if (emp != null) {
                                    DeviceIdentity(
                                        role = DeviceRole.DRIVER,
                                        employeeId = emp.id,
                                        employeeRemoteId = emp.firestoreId,
                                        employeeName = emp.name,
                                        phone = cleanPhone
                                    )
                                } else {
                                    // 本机兜底：手机号不在员工表也能先登录（离线场景）。
                                    // 员工名先用手机号，等连上服务器、营业员添加他之后会自动补绑。
                                    DeviceIdentity(
                                        role = DeviceRole.DRIVER,
                                        employeeName = cleanPhone,
                                        phone = cleanPhone
                                    )
                                }
                            }
                            else -> null
                        }
                    }
                    if (identity != null) {
                        onLoggedIn(identity)
                    } else {
                        error = "手机号或密码不对"
                        checking = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(62.dp),
            enabled = !checking
        ) {
            Text(if (checking) "登录中…" else "登录", fontSize = 21.sp)
        }
    }
}
