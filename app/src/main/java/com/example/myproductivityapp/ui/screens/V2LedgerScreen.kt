package com.example.myproductivityapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myproductivityapp.data.AppDatabase
import com.example.myproductivityapp.data.local.DeviceIdentity
import com.example.myproductivityapp.data.local.DeviceRole
import com.example.myproductivityapp.data.model.DeliveryRecord
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun V2LedgerScreen(identity: DeviceIdentity) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    var records by remember { mutableStateOf<List<DeliveryRecord>>(emptyList()) }

    LaunchedEffect(Unit) {
        db.deliveryRecordDao().getAllRecords().collect { all ->
            records = if (identity.role == DeviceRole.DRIVER) {
                val remoteId = identity.employeeRemoteId
                // 送气工只统计自己的记录：优先服务器稳定 ID；
                // 老数据 employeeFirestoreId 为空时用姓名兜底（新记录保存时已带上 remoteId，不会串人）。
                all.filter { rec ->
                    if (remoteId.isNotBlank()) {
                        rec.employeeFirestoreId == remoteId ||
                            (rec.employeeFirestoreId.isBlank() && rec.employeeName == identity.employeeName)
                    } else {
                        rec.employeeName == identity.employeeName
                    }
                }
            } else {
                // 营业员/站长看全站
                all
            }
        }
    }

    val calendar = remember { Calendar.getInstance() }
    val startOfDay = remember {
        calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val todayRecords = records.filter { it.date >= startOfDay }
    val todayQty = todayRecords.sumOf { it.quantity }
    val todayAmount = todayRecords.sumOf { it.totalAmount }
    val todayDebt = todayRecords.sumOf { it.debtAmount }
    val allDebt = records.filter { it.debtAmount > 0.0 }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text("统计", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                if (identity.role == DeviceRole.DRIVER) "只看你自己的记录。" else "先看今天，再看欠款。",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("今天", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("$todayQty 瓶", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("金额 ¥${String.format("%.0f", todayAmount)}", fontSize = 19.sp)
                    if (todayDebt > 0) {
                        Text(
                            "今天未收 ¥${String.format("%.0f", todayDebt)}",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        val byEmployee = todayRecords.groupBy { it.employeeName }
            .mapValues { (_, list) -> Pair(list.sumOf { it.quantity }, list.sumOf { it.totalAmount }) }
            .toList()
            .sortedByDescending { it.second.first }

        if (byEmployee.isNotEmpty()) {
            item { Text("今天每个人", fontSize = 21.sp, fontWeight = FontWeight.Bold) }
            items(byEmployee, key = { it.first }) { (name, summary) ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(name.ifBlank { "未命名" }, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("${summary.first}瓶　¥${String.format("%.0f", summary.second)}", fontSize = 18.sp)
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text("欠款", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        if (allDebt.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("没有记录到欠款", modifier = Modifier.padding(16.dp), fontSize = 18.sp)
                }
            }
        } else {
            items(allDebt.take(30), key = { it.id }) { record ->
                DebtRecordCard(record)
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text("最近记录", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        items(records.take(30), key = { "recent-${it.id}" }) { record ->
            SimpleRecordCard(record)
        }
    }
}

@Composable
private fun DebtRecordCard(record: DeliveryRecord) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(record.employeeName, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(
                    "欠 ¥${String.format("%.0f", record.debtAmount)}",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (record.notes.isNotBlank()) Text(record.notes, maxLines = 2)
        }
    }
}

@Composable
private fun SimpleRecordCard(record: DeliveryRecord) {
    val format = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(record.employeeName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(format.format(Date(record.date)))
            }
            Text("${record.quantity}瓶　¥${String.format("%.0f", record.totalAmount)}")
            if (record.debtAmount > 0) {
                Text("未收 ¥${String.format("%.0f", record.debtAmount)}", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
