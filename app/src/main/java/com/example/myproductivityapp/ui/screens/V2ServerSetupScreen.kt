package com.example.myproductivityapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myproductivityapp.data.local.ServerConfig
import com.example.myproductivityapp.data.remote.LocalServerClient
import kotlinx.coroutines.launch

@Composable
fun V2ServerSetupScreen(
    initial: ServerConfig? = null,
    onConnected: (ServerConfig) -> Unit
) {
    var url by remember { mutableStateOf(initial?.baseUrl ?: "http://192.168.1.100:8000") }
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "gas-station-local") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(22.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("连接新站服务器", fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("这一步只需要配置一次。小主机和手机要先连在同一个网络测试。", fontSize = 18.sp)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("服务器地址") },
            supportingText = { Text("例如：http://192.168.1.50:8000") },
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("连接密码") },
            supportingText = { Text("测试默认：gas-station-local") },
            singleLine = true
        )

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 17.sp)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (checking) return@Button
                val cleanUrl = url.trim().trimEnd('/')
                val cleanKey = apiKey.trim()
                if (cleanUrl.isBlank() || cleanKey.isBlank()) {
                    error = "服务器地址和连接密码都要填写"
                    return@Button
                }
                checking = true
                error = null
                scope.launch {
                    val client = LocalServerClient(cleanUrl, cleanKey)
                    if (client.health()) {
                        onConnected(ServerConfig(cleanUrl, cleanKey))
                    } else {
                        error = "连不上服务器。先确认小主机服务已启动、IP填写正确。"
                    }
                    checking = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(62.dp),
            enabled = !checking
        ) {
            Text(if (checking) "正在测试连接…" else "测试并保存", fontSize = 21.sp)
        }
    }
}
