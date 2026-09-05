package com.example.myproductivityapp.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myproductivityapp.MainActivity
import com.example.myproductivityapp.data.AppDatabase
import com.example.myproductivityapp.data.local.DeviceIdentity
import com.example.myproductivityapp.data.local.DeviceRole
import com.example.myproductivityapp.data.model.DeliveryRecord
import com.example.myproductivityapp.data.remote.PhotoUtil
import com.example.myproductivityapp.data.repository.DeliveryRecordRepository
import com.example.myproductivityapp.ui.components.PhotoViewerDialog
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun V2LedgerScreen(identity: DeviceIdentity) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf<List<DeliveryRecord>>(emptyList()) }
    var editingRecord by remember { mutableStateOf<DeliveryRecord?>(null) }
    var deletingRecord by remember { mutableStateOf<DeliveryRecord?>(null) }
    // 删除提交中锁：防止网络卡顿时连点删除重复提交
    var deleting by remember { mutableStateOf(false) }
    // 筛选：关键词 / 时间范围 / 排序（新→旧=倒序默认）
    var searchText by remember { mutableStateOf("") }
    var fromDateText by remember { mutableStateOf("") }
    var toDateText by remember { mutableStateOf("") }
    var sortDesc by remember { mutableStateOf(true) }
    // 营业员/站长保存改动的记录（含年份级对瓶切换），写本地 + 云端
    val saveRecord: (DeliveryRecord) -> Unit = { updated ->
        scope.launch {
            DeliveryRecordRepository(db.deliveryRecordDao(), MainActivity.cloudClient).update(updated)
        }
    }

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

    // 日期文本 yyyy-MM-dd -> 当天毫秒；endOfDay=true 取当天 23:59:59；空/非法=不限
    fun dayMillis(text: String, endOfDay: Boolean): Long? {
        if (text.isBlank()) return null
        val parts = text.trim().split("-")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val mo = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        val cal = Calendar.getInstance().apply {
            clear()
            set(y, mo - 1, d, if (endOfDay) 23 else 0, if (endOfDay) 59 else 0, if (endOfDay) 59 else 0)
        }
        return cal.timeInMillis
    }
    val fromMs = dayMillis(fromDateText, false)
    val toMs = dayMillis(toDateText, true)
    val keyword = searchText.trim()
    // 全部记录按筛选条件过滤 + 排序（DAO 本身 date DESC，正序时反转）
    val visibleRecords = records.filter { rec ->
        val inRange = (fromMs == null || rec.date >= fromMs) && (toMs == null || rec.date <= toMs)
        val hit = keyword.isEmpty() ||
            rec.employeeName.contains(keyword) ||
            rec.notes.contains(keyword) ||
            String.format("%.0f", rec.totalAmount).contains(keyword)
        inRange && hit
    }.let { if (sortDesc) it else it.asReversed() }

    val todayTitle = remember {
        SimpleDateFormat("M月d日 EEEE", Locale.CHINESE).format(Date())
    }
    val todayRecords = visibleRecords.filter { it.date >= startOfDay }
    val todayQty = todayRecords.sumOf { recordMainQty(it) }
    val todayAmount = todayRecords.sumOf { it.totalAmount }

    // 以往记录按天分组（key=当天零点），分组按天倒序；组内沿用记录本身顺序。
    val dayGroups = remember(visibleRecords) {
        val map = LinkedHashMap<Long, MutableList<DeliveryRecord>>()
        val cal = Calendar.getInstance()
        for (rec in visibleRecords) {
            cal.timeInMillis = rec.date
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            map.getOrPut(cal.timeInMillis) { mutableListOf() }.add(rec)
        }
        map.toList().sortedByDescending { it.first }
    }
    val expandedDays = remember { mutableStateMapOf<Long, Boolean>() }
    // 员工当天总结展开状态，key = "${dayStart}:${employeeName}"
    val expandedEmployees = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("搜员工/备注/金额") },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 17.sp)
                    )
                    OutlinedButton(onClick = { sortDesc = !sortDesc }) {
                        Text(if (sortDesc) "新→旧 ↓" else "旧→新 ↑", fontSize = 15.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = fromDateText,
                        onValueChange = { fromDateText = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("从 2026-09-01") },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 15.sp)
                    )
                    OutlinedTextField(
                        value = toDateText,
                        onValueChange = { toDateText = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("到 2026-09-30") },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 15.sp)
                    )
                }
                if (searchText.isNotBlank() || fromDateText.isNotBlank() || toDateText.isNotBlank()) {
                    TextButton(onClick = {
                        searchText = ""
                        fromDateText = ""
                        toDateText = ""
                    }) { Text("清除筛选", fontSize = 15.sp) }
                }
            }
        }
        item {
            Text("今天", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                if (identity.role == DeviceRole.DRIVER) "只看你自己的记录。" else "先看今天，再看记录。",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(todayTitle, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("$todayQty 瓶", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("金额 ¥${String.format("%.0f", todayAmount)}", fontSize = 19.sp)
                }
            }
        }

        val byEmployee = todayRecords.groupBy { it.employeeName }
            .mapValues { (_, list) -> Pair(list.sumOf { recordMainQty(it) }, list.sumOf { it.totalAmount }) }
            .toList()
            .sortedByDescending { it.second.first }

        if (identity.role != DeviceRole.DRIVER && byEmployee.isNotEmpty()) {
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
            Text("以往记录", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        if (visibleRecords.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("还没有记录", modifier = Modifier.padding(16.dp), fontSize = 18.sp)
                }
            }
        } else {
            dayGroups.forEach { (day, list) ->
                item(key = "day-$day") {
                    DayHeaderCard(
                        day = day,
                        list = list,
                        expanded = expandedDays[day] == true,
                        onClick = { expandedDays[day] = expandedDays[day] != true }
                    )
                }
                if (expandedDays[day] == true) {
                    if (identity.role == DeviceRole.DRIVER) {
                        // 送气工：保持现状，直接列出自己当天的记录，不按员工分组
                        items(list, key = { it.id }) { record ->
                            RecordCard(
                                record = record,
                                canEdit = false,
                                onEdit = { editingRecord = record },
                                onUpdate = saveRecord
                            )
                        }
                    } else {
                        // 营业员/站长：先按员工分"当天总结"卡片，展开后再列该员工明细
                        val dayByEmployee = list.groupBy { it.employeeName }
                            .mapValues { (_, l) -> l }
                            .toList()
                            .sortedByDescending { (_, l) -> l.sumOf { recordMainQty(it) } }
                        dayByEmployee.forEach { (name, empList) ->
                            val empKey = "$day:$name"
                            item(key = "emp-$empKey") {
                                EmployeeDaySummaryCard(
                                    employeeName = name,
                                    list = empList,
                                    expanded = expandedEmployees[empKey] == true,
                                    onClick = { expandedEmployees[empKey] = expandedEmployees[empKey] != true }
                                )
                            }
                            if (expandedEmployees[empKey] == true) {
                                items(empList, key = { it.id }) { record ->
                                    RecordCard(
                                        record = record,
                                        canEdit = true,
                                        onEdit = { editingRecord = record },
                                        onUpdate = saveRecord,
                                        onDelete = { deletingRecord = record }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingRecord?.let { record ->
        EditDeliveryRecordDialog(
            record = record,
            onDismiss = { editingRecord = null },
            onSave = { updated ->
                scope.launch {
                    try {
                        val cloudOk = DeliveryRecordRepository(
                            db.deliveryRecordDao(),
                            MainActivity.cloudClient
                        ).updateWithResult(updated)
                        Toast.makeText(
                            context,
                            if (cloudOk) "修改成功" else "修改已保存，服务器没连上，稍后自动同步",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "保存失败：${e.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                    }
                    editingRecord = null
                }
            }
        )
    }

    deletingRecord?.let { record ->
        AlertDialog(
            onDismissRequest = { if (!deleting) deletingRecord = null },
            title = { Text("删除这条记录？", fontSize = 22.sp, fontWeight = FontWeight.Bold) },
            text = { Text("删除后手机和服务器都会移除，无法恢复。", fontSize = 18.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        deleting = true
                        scope.launch {
                            try {
                                val cloudOk = DeliveryRecordRepository(
                                    db.deliveryRecordDao(),
                                    MainActivity.cloudClient
                                ).deleteWithResult(record)
                                Toast.makeText(
                                    context,
                                    if (cloudOk) "删除成功" else "已删除，服务器没连上，稍后自动同步",
                                    Toast.LENGTH_LONG
                                ).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "删除失败：${e.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                            }
                            deletingRecord = null
                            deleting = false
                        }
                    },
                    enabled = !deleting
                ) { Text(if (deleting) "删除中…" else "删除", fontSize = 19.sp) }
            },
            dismissButton = {
                TextButton(onClick = { deletingRecord = null }, enabled = !deleting) { Text("取消") }
            }
        )
    }

}

@Composable
private fun DayHeaderCard(day: Long, list: List<DeliveryRecord>, expanded: Boolean, onClick: () -> Unit) {
    val dayTitle = remember(day) { SimpleDateFormat("M月d日 EEEE", Locale.CHINESE).format(Date(day)) }
    val qty = list.sumOf { recordMainQty(it) }
    val amount = list.sumOf { it.totalAmount }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(dayTitle, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(if (expanded) "▲" else "▼", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${list.size}条 · ${qty}瓶 · ¥${String.format("%.0f", amount)}", fontSize = 16.sp)
        }
    }
}

@Composable
private fun EmployeeDaySummaryCard(employeeName: String, list: List<DeliveryRecord>, expanded: Boolean, onClick: () -> Unit) {
    val qty = list.sumOf { recordMainQty(it) }
    val amount = list.sumOf { it.totalAmount }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(employeeName.ifBlank { "未命名" }, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(if (expanded) "▲" else "▼", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${list.size}条 · ${qty}瓶 · ¥${String.format("%.0f", amount)}", fontSize = 16.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordCard(
    record: DeliveryRecord,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onUpdate: (DeliveryRecord) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    // 点按照片放大查看（记录可能有多张照片，记住点的是哪张；URL 或本地文件均可）
    var zoomPhoto by remember { mutableStateOf<Any?>(null) }
    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val exchangeSeg = exchangeSegment(record.notes)
    val pureExchange = isPureExchange(record.notes)
    val notesShown = notesWithoutExtracted(record.notes)
    val rentalSeg = rentalSegment(record.notes)
    val exchangeYearInfo = exchangeSeg?.let { parseYearInfo(it) } ?: emptyList()
    // 解析 returnedYear：新格式 "年份:已回数" 空格分隔；老格式 "、"分隔年份集合（整个年份已回）
    val returnedCounts = parseReturnedCounts(record.returnedYear, exchangeYearInfo).toMutableMap()
    // 老数据兜底：returnedYear 为空但 exchangeStatus 为 RETURNED → 全部年份按已回处理
    if (record.returnedYear.isBlank() && record.exchangeStatus == "RETURNED") {
        exchangeYearInfo.forEach { (y, total) -> returnedCounts[y] = total }
    }
    val newCount = bottleSegment(record.notes, "新瓶")?.let { parseBottleCount(it) }
    val smallCount = bottleSegment(record.notes, "小瓶")?.let { parseBottleCount(it) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // 第一行：员工名（左）+ 总金额（右，纯对瓶不显示 ¥0）
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(record.employeeName.ifBlank { "未命名" }, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (!pureExchange) {
                    Text("¥${String.format("%.0f", record.totalAmount)}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            // 第二行：时间（MM-dd HH:mm），小字灰色
            Text(
                timeFormat.format(Date(record.date)),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // 第三行：总数量（重瓶+小瓶=销售瓶数；纯对瓶/租瓶/自定义单回退原数量）
            Text("${recordMainQty(record)}瓶", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            // 收款行：现金 + 微信 + 增加/减去，只显示非零/存在的项，强制单行（微信 = 总金额 - 现金）
            val wechatPay = (record.totalAmount - record.cashAmount).coerceAtLeast(0.0)
            val addSeg = bottleSegment(record.notes, "增加")
            val subSeg = bottleSegment(record.notes, "减去")
            val payParts = mutableListOf<String>().apply {
                if (record.cashAmount > 0) add("现金 ¥${String.format("%.0f", record.cashAmount)}")
                if (wechatPay > 0) add("微信 ¥${String.format("%.0f", wechatPay)}")
                if (addSeg != null) add("增加 " + addSeg.removePrefix("增加:").trim())
                if (subSeg != null) add("减去 " + subSeg.removePrefix("减去:").trim())
            }
            if (payParts.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    payParts.forEach { part ->
                        Text(part, fontSize = 16.sp)
                    }
                }
            }
            // 租瓶行
            if (rentalSeg != null) {
                val obj = parseObjectName(rentalSeg)
                val yearInfo = formatYearInfo(parseYearInfo(rentalSeg))
                Text(
                    when {
                        obj.isNotBlank() && yearInfo.isNotBlank() -> "租瓶：$obj $yearInfo"
                        obj.isNotBlank() -> "租瓶：$obj"
                        else -> "租瓶：$yearInfo"
                    },
                    fontSize = 16.sp
                )
            }
            // 新瓶/小瓶行
            val newSmallParts = mutableListOf<String>().apply {
                if (newCount != null) add("新瓶 ${newCount}个")
                if (smallCount != null) add("小瓶 ${smallCount}个")
            }
            if (newSmallParts.isNotEmpty()) {
                Text(newSmallParts.joinToString("　　"), fontSize = 16.sp)
            }
            // 自定义瓶型行（价格设置里添加的类型，如"中瓶"）：只显示类型和数量，不带金额
            val extraParts = extraBottleParts(record.notes)
            if (extraParts.isNotEmpty()) {
                Text(
                    extraParts.joinToString("　　") { "${it.first}：${it.second}瓶" },
                    fontSize = 16.sp
                )
            }
            // 对瓶行：年份列表（不显示括号/总数，单瓶只显示年份）
            if (exchangeSeg != null) {
                if (exchangeYearInfo.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text("对瓶：", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        FlowRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            exchangeYearInfo.forEach { (year, count) ->
                                Text(
                                    if (count <= 1) year else "$year${count}个",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                } else {
                    // 解析不出年份（括号缺失等异常）时回退到原样
                    Text(exchangeSeg, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                }
            }
            // 对瓶状态区：逐年份 已回 X 未回 Y（canEdit 时带 已回+1/已回-1 按钮）
            if (exchangeSeg != null) {
                if (exchangeYearInfo.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        exchangeYearInfo.forEach { (year, total) ->
                            val returned = (returnedCounts[year] ?: 0).coerceIn(0, total)
                            ExchangeYearStatus(
                                year = year,
                                total = total,
                                returnedCount = returned,
                                clickable = canEdit,
                                onAddReturned = {
                                    if (returned < total) {
                                        val next = returnedCounts + (year to returned + 1)
                                        val allDone = exchangeYearInfo.all { (y, t) -> (next[y] ?: 0) >= t }
                                        onUpdate(
                                            record.copy(
                                                exchangeStatus = if (allDone) "RETURNED" else "PENDING",
                                                returnedYear = exchangeYearInfo.joinToString(" ") { (y, _) -> "$y:${next[y] ?: 0}" }
                                            )
                                        )
                                    }
                                },
                                onSubReturned = {
                                    if (returned > 0) {
                                        val next = returnedCounts + (year to returned - 1)
                                        val allDone = exchangeYearInfo.all { (y, t) -> (next[y] ?: 0) >= t }
                                        onUpdate(
                                            record.copy(
                                                exchangeStatus = if (allDone) "RETURNED" else "PENDING",
                                                returnedYear = exchangeYearInfo.joinToString(" ") { (y, _) -> "$y:${next[y] ?: 0}" }
                                            )
                                        )
                                    }
                                }
                            )
                        }
                    }
                } else if (record.exchangeStatus == "PENDING" || record.exchangeStatus == "RETURNED") {
                    Text(
                        if (record.exchangeStatus == "RETURNED") "已回" else "未回",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (record.exchangeStatus == "RETURNED")
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
            }
            // 备注（去掉对瓶/租瓶/新瓶/小瓶段后的剩余内容）
            if (notesShown.isNotBlank()) {
                Text(notesShown.removePrefix("备注:").trim(), maxLines = 2)
            }
            // 照片：优先服务器 URL（跨手机可见，压缩图加载快）；未上传的本机照片按本地路径显示
            val remoteUrls = PhotoUtil.remoteUrlList(record.remoteImages)
            val recordPhotos = if (remoteUrls.isNotEmpty()) {
                remoteUrls
            } else {
                listOfNotNull(
                    record.imagePath.takeIf { it.isNotBlank() },
                    record.imageUrl.takeIf { it.isNotBlank() }
                ).distinct()
            }
            if (recordPhotos.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recordPhotos.forEach { p ->
                        val model: Any? = if (p.startsWith("http")) p else File(p).takeIf { it.exists() }
                        if (model != null) {
                            AsyncImage(
                                model = model,
                                contentDescription = "记录照片，点按放大",
                                modifier = Modifier
                                    .size(width = 120.dp, height = 90.dp)
                                    .clickable { zoomPhoto = model },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
                Text(
                    "点按照片可放大",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (zoomPhoto != null) {
                PhotoViewerDialog(imageData = zoomPhoto, onDismiss = { zoomPhoto = null })
            }
            // 按钮行：修改 / 删除
            if (canEdit) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (onDelete != null) {
                        TextButton(onClick = onDelete) {
                            Text("删除", fontSize = 18.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = onEdit) {
                        Text("修改", fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExchangeYearStatus(
    year: String,
    total: Int,
    returnedCount: Int,
    clickable: Boolean,
    onAddReturned: () -> Unit,
    onSubReturned: () -> Unit
) {
    val returned = returnedCount.coerceIn(0, total)
    val remaining = total - returned
    // 对瓶按钮颜色：未回=error 红，已回=深绿
    val green = Color(0xFF2E7D32)
    val errorColor = MaterialTheme.colorScheme.error
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("$year", fontSize = 17.sp, fontWeight = FontWeight.Medium)
        if (clickable) {
            // 营业员/站长：一个按钮。未回 N>0 → 显示（未回N），点一下已回+1；
            // 全部已回 → 显示（已回），不可再点（按错了用卡片上的“修改”按钮改回）。
            OutlinedButton(
                onClick = onAddReturned,
                enabled = remaining > 0,
                modifier = Modifier.height(38.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (remaining > 0) errorColor else green,
                    disabledContentColor = green
                )
            ) {
                Text(if (remaining > 0) "未回$remaining" else "已回", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            // 送气工：只读显示状态文字
            Text(
                if (remaining > 0) "未回$remaining" else "已回",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (remaining > 0) errorColor else green
            )
        }
    }
}

/** 解析 returnedYear 得到每个年份的已回数量。
 * 新格式：空格分隔的 "年份:已回数"，如 "22厂:1 23检:2"；
 * 老格式："、"分隔的年份集合，如 "22检、23检"，该年份整体已回（已回数 = 该年份总数）。
 * 返回 Map<年份, 已回数>，未出现的年份视为 0（不在 map 里）。
 */
private fun parseReturnedCounts(returnedYear: String, years: List<Pair<String, Int>>): Map<String, Int> {
    val totals = years.toMap()
    val result = mutableMapOf<String, Int>()
    if (returnedYear.isBlank()) return result
    returnedYear.split(" ").filter { it.isNotBlank() }.forEach { seg ->
        if (seg.contains(":")) {
            val idx = seg.lastIndexOf(':')
            val year = seg.substring(0, idx).trim()
            val count = seg.substring(idx + 1).trim().toIntOrNull() ?: 0
            if (year.isNotBlank()) result[year] = count
        } else {
            // 老格式：按 "、" 拆成年份，每个年份视为整体已回
            seg.split("、").filter { it.isNotBlank() }.forEach { y ->
                result[y] = totals[y] ?: 1
            }
        }
    }
    return result
}

@Composable
private fun EditDeliveryRecordDialog(
    record: DeliveryRecord,
    onDismiss: () -> Unit,
    onSave: (DeliveryRecord) -> Unit
) {
    var quantityText by remember { mutableStateOf(record.quantity.toString()) }
    var totalText by remember { mutableStateOf(if (record.totalAmount == 0.0) "" else String.format("%.0f", record.totalAmount)) }
    var cashText by remember { mutableStateOf(if (record.cashAmount == 0.0) "" else String.format("%.0f", record.cashAmount)) }
    var wechatText by remember { mutableStateOf(if (record.wechatAmount == 0.0) "" else String.format("%.0f", record.wechatAmount)) }
    // 备注框只编辑"备注:"段正文；瓶型/金额段原样保留，保存时重组
    val otherSegs = remember(record) {
        record.notes.split(" | ").filter { it.trim().isNotBlank() && !it.trim().startsWith("备注:") }
    }
    var noteText by remember {
        mutableStateOf(
            record.notes.split(" | ").firstOrNull { it.trim().startsWith("备注:") }
                ?.substringAfter("备注:")?.trim() ?: ""
        )
    }
    // 对瓶归还编辑：从 notes 解析标记+总数，已回数可 +/- 调整
    val exchangeYears = remember(record) {
        exchangeSegment(record.notes)?.let { parseYearInfo(it) } ?: emptyList()
    }
    val returnedCounts = remember(record) {
        mutableStateMapOf<String, Int>().apply {
            parseReturnedCounts(record.returnedYear, exchangeYears).forEach { (y, c) -> put(y, c) }
        }
    }
    // 提交中锁：防止网络卡顿时连点保存重复提交
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改记录", fontSize = 24.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text("数量") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 21.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = totalText,
                    onValueChange = { totalText = it },
                    label = { Text("总金额") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 21.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = cashText,
                    onValueChange = { cashText = it },
                    label = { Text("现金收款") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 21.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = wechatText,
                    onValueChange = { wechatText = it },
                    label = { Text("微信收款") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 21.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("备注（记情况，如：26厂拿错成25厂）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    textStyle = LocalTextStyle.current.copy(fontSize = 21.sp)
                )
                if (exchangeYears.isNotEmpty()) {
                    Divider()
                    Text("对瓶归还（按错了在这里改）", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    exchangeYears.forEach { (year, total) ->
                        val returned = (returnedCounts[year] ?: 0).coerceIn(0, total)
                        val remaining = total - returned
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (remaining > 0) "$year 未回$remaining" else "$year 已回",
                                Modifier.weight(1f),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (remaining > 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                            )
                            TextButton(
                                onClick = { if (returned > 0) returnedCounts[year] = returned - 1 },
                                enabled = returned > 0
                            ) { Text("−", fontSize = 22.sp) }
                            Text("已回 $returned / $total", fontSize = 15.sp)
                            TextButton(
                                onClick = { if (returned < total) returnedCounts[year] = returned + 1 },
                                enabled = returned < total
                            ) { Text("+", fontSize = 22.sp) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!saving) {
                        saving = true
                        val cash = cashText.toDoubleOrNull() ?: 0.0
                        val wechat = wechatText.toDoubleOrNull() ?: 0.0
                        val total = totalText.toDoubleOrNull() ?: record.totalAmount
                        val debt = (total - cash - wechat).coerceAtLeast(0.0)
                        val hasExchange = exchangeYears.isNotEmpty()
                        onSave(
                            record.copy(
                                quantity = quantityText.toIntOrNull() ?: record.quantity,
                                totalAmount = total,
                                cashAmount = cash,
                                wechatAmount = wechat,
                                debtAmount = debt,
                                notes = buildList {
                                    addAll(otherSegs)
                                    if (noteText.trim().isNotBlank()) add("备注: ${noteText.trim()}")
                                }.joinToString(" | "),
                                exchangeStatus = if (hasExchange) {
                                    if (exchangeYears.all { (y, t) -> (returnedCounts[y] ?: 0) >= t }) "RETURNED" else "PENDING"
                                } else record.exchangeStatus,
                                returnedYear = if (hasExchange) {
                                    exchangeYears.joinToString(" ") { (y, _) -> "$y:${returnedCounts[y] ?: 0}" }
                                } else record.returnedYear
                            )
                        )
                    }
                }
            ) { Text(if (saving) "保存中…" else "保存", fontSize = 19.sp) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 从 notes 里解析"对瓶:"段，返回原样段（如 "对瓶: 3瓶 (22检:1个 23检:2个)"），没有则返回 null。 */
private fun exchangeSegment(notes: String): String? =
    notes.split(" | ").firstOrNull { it.trim().startsWith("对瓶:") }?.trim()

/** 是否纯对瓶记录：除了对瓶段和备注段外没有其他瓶子段。 */
private fun isPureExchange(notes: String): Boolean {
    if (exchangeSegment(notes) == null) return false
    return notes.split(" | ").none {
        val t = it.trim()
        t.isNotBlank() && !t.startsWith("对瓶:") && !t.startsWith("备注:") && t.contains("瓶:")
    }
}

/** 从 notes 里解析指定前缀的段（如 "新瓶:"/"小瓶:"/"租瓶:"），返回原样段，没有则返回 null。 */
private fun bottleSegment(notes: String, prefix: String): String? =
    notes.split(" | ").firstOrNull { it.trim().startsWith("$prefix:") }?.trim()

/** 租瓶段，如 "租瓶: 2瓶 (22检:1个 23检:1个) [对象: 张三]"。 */
private fun rentalSegment(notes: String): String? = bottleSegment(notes, "租瓶")

/** 从瓶子段里解析对象名 "[对象: xxx]"，没有则返回空串。 */
private fun parseObjectName(segment: String): String =
    Regex("""\[对象:\s*([^\]]*)]""").find(segment)?.groupValues?.get(1)?.trim() ?: ""

/** 从段内 "(...)" 里解析 year:count 列表，如 "22检:1个 23检:2个" -> [("22检",1),("23检",2)]。 */
private fun parseYearInfo(segment: String): List<Pair<String, Int>> {
    val paren = Regex("""\(([^)]*)\)""").find(segment)?.groupValues?.get(1) ?: return emptyList()
    return paren.split(" ").map { it.trim() }.filter { it.isNotBlank() }.mapNotNull { token ->
        val idx = token.lastIndexOf(':')
        if (idx <= 0) return@mapNotNull null
        val year = token.substring(0, idx).trim()
        val count = token.substring(idx + 1).trim().removeSuffix("个").trim().toIntOrNull() ?: 1
        if (year.isBlank()) null else year to count
    }
}

/** 格式化年份信息：count==1 只显示年份，count>1 显示 "年份:count个"，多个用空格分隔。 */
private fun formatYearInfo(years: List<Pair<String, Int>>): String =
    years.joinToString(" ") { (y, c) -> if (c <= 1) y else "$y:${c}个" }

/** 从段里解析瓶子数量（N瓶 的 N）。 */
private fun parseBottleCount(segment: String): Int? =
    Regex("""(\d+)瓶""").find(segment)?.groupValues?.get(1)?.toIntOrNull()

/** 内置瓶型的显示名（notes 段里的中文名）。 */
private val BUILTIN_BOTTLE_NAMES = setOf("对瓶", "租瓶", "重瓶", "新瓶", "小瓶")

/** 从 notes 解析自定义瓶型段："对新: 5瓶 × ¥70" -> [("对新", 5)]。内置类型不算。 */
private fun extraBottleParts(notes: String): List<Pair<String, Int>> {
    // 类型名不限"瓶"结尾（用户可能起"对新"这种名），只要是 "名字: N瓶 × ¥价" 格式
    val re = Regex("""^([^:]+):\s*(\d+)瓶\s*×\s*¥\d+""")
    return notes.split(" | ").mapNotNull { seg ->
        val t = seg.trim()
        val m = re.find(t) ?: return@mapNotNull null
        val name = m.groupValues[1].trim()
        if (name in BUILTIN_BOTTLE_NAMES) null
        else name to (m.groupValues[2].toIntOrNull() ?: 0)
    }.filter { it.second > 0 }
}

/** 去掉瓶子段/增加/减去段，避免和卡片上方专行显示重复。
 * 瓶型段统一识别为 "名字: N瓶" 开头（内置+自定义都算），类型名不限"瓶"结尾。 */
private fun notesWithoutExtracted(notes: String): String =
    notes.split(" | ").filterNot { seg ->
        val t = seg.trim()
        t.startsWith("增加:") || t.startsWith("减去:") ||
            Regex("""^[^:]+:\s*\d+瓶""").containsMatchIn(t)
    }.joinToString(" | ").trim()

/**
 * 卡片主数量：重瓶 + 小瓶（销售瓶数）。
 * 没有重瓶/小瓶的纯附属记录（对瓶/租瓶/自定义瓶型单）回退原总数量，避免显示 0。
 * 例子：1 重瓶 + 1 新瓶 -> 卡片大字 1，明细行另有 "新瓶 1个"。
 */
private fun recordMainQty(record: DeliveryRecord): Int {
    val heavy = bottleSegment(record.notes, "重瓶")?.let { parseBottleCount(it) } ?: 0
    val small = bottleSegment(record.notes, "小瓶")?.let { parseBottleCount(it) } ?: 0
    val main = heavy + small
    return if (main > 0) main else record.quantity
}
