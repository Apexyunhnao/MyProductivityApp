package com.example.myproductivityapp.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.myproductivityapp.MainActivity
import com.example.myproductivityapp.data.AppDatabase
import com.example.myproductivityapp.data.model.*
import com.example.myproductivityapp.data.repository.BottleYearRepository
import com.example.myproductivityapp.data.repository.DeliveryRecordRepository
import com.example.myproductivityapp.data.repository.EmployeeRepository
import com.example.myproductivityapp.data.repository.PriceConfigRepository
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** V2 高频记账：一屏完成选人、记瓶、记钱、保存。 */
@Composable
fun V2RecordScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val client = MainActivity.cloudClient
    val scope = rememberCoroutineScope()

    val employeeRepo = remember { EmployeeRepository(db.employeeDao(), client) }
    val priceRepo = remember { PriceConfigRepository(db.priceConfigDao(), client) }
    val yearRepo = remember { BottleYearRepository(db.bottleYearDao(), client) }
    val recordRepo = remember { DeliveryRecordRepository(db.deliveryRecordDao(), client) }

    var employees by remember { mutableStateOf<List<Employee>>(emptyList()) }
    var employee by remember { mutableStateOf<Employee?>(null) }
    var years by remember { mutableStateOf<List<BottleYear>>(emptyList()) }
    var prices by remember { mutableStateOf<Map<BottleType, Int>>(emptyMap()) }

    var heavy by remember { mutableIntStateOf(0) }
    var fresh by remember { mutableIntStateOf(0) }
    var small by remember { mutableIntStateOf(0) }
    val rentalMarks = remember { mutableStateMapOf<String, Int>() }
    val exchangeMarks = remember { mutableStateMapOf<String, Int>() }
    var rentalCustomer by remember { mutableStateOf("") }

    var cashText by remember { mutableStateOf("") }
    var extraText by remember { mutableStateOf("") }
    var prepayText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // 拍照：租瓶押金单（imagePath）+ 备注照片（imageUrl，复用旧字段做第二张图的最小兼容）
    var rentalImagePath by remember { mutableStateOf("") }
    var noteImagePath by remember { mutableStateOf("") }
    var pendingPhotoSlot by remember { mutableStateOf("") } // "rental" | "note"
    var photoFile by remember { mutableStateOf<File?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    fun createPhotoFile(context: android.content.Context): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dir = File(context.filesDir, "delivery_images")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "IMG_${stamp}.jpg")
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { _ ->
        val file = photoFile
        val hasContent = file?.let { it.exists() && it.length() > 0 } ?: false
        if (hasContent && file != null) {
            when (pendingPhotoSlot) {
                "rental" -> rentalImagePath = file.absolutePath
                "note" -> noteImagePath = file.absolutePath
            }
        } else {
            file?.delete()
        }
        photoFile = null
        pendingPhotoSlot = ""
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = createPhotoFile(context)
            photoFile = file
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraLauncher.launch(uri)
        } else {
            showPermissionDialog = true
        }
    }

    fun requestTakePhoto(slot: String) {
        pendingPhotoSlot = slot
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(Unit) {
        employeeRepo.observeAll().collect { list ->
            employees = list
            if (employee == null) employee = list.firstOrNull()
        }
    }
    LaunchedEffect(Unit) {
        yearRepo.observeAll().collect { years = it.distinctBy { y -> "${y.year}-${y.type}" } }
    }
    LaunchedEffect(Unit) {
        val loaded = mutableMapOf<BottleType, Int>()
        BottleType.values().forEach { type ->
            loaded[type] = priceRepo.getByType(type.name)?.price ?: 0
        }
        prices = loaded
    }

    val rentalQty = rentalMarks.values.sum()
    val exchangeQty = exchangeMarks.values.sum()
    val totalQty = heavy + rentalQty + exchangeQty + fresh + small
    val total =
        heavy * (prices[BottleType.HEAVY] ?: 0) +
        rentalQty * (prices[BottleType.RENTAL] ?: 0) +
        fresh * (prices[BottleType.NEW] ?: 0) +
        small * (prices[BottleType.SMALL] ?: 0)

    val extra = extraText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val prepay = prepayText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val receivable = (total + extra - prepay).coerceAtLeast(0.0)
    val cash = cashText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val wechat = (receivable - cash).coerceAtLeast(0.0)
    val marks = years.map { "${it.year}${it.type}" }.distinct()

    fun reset() {
        heavy = 0
        fresh = 0
        small = 0
        rentalMarks.clear()
        exchangeMarks.clear()
        rentalCustomer = ""
        cashText = ""
        extraText = ""
        prepayText = ""
        note = ""
        rentalImagePath = ""
        noteImagePath = ""
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Title("送气人员")
            if (employees.isEmpty()) {
                Text("还没有员工，请先到右上角设置里添加。")
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(employees, key = { it.id }) { item ->
                        FilterChip(
                            selected = employee?.id == item.id,
                            onClick = { employee = item },
                            label = { Text(item.name, fontSize = 18.sp) }
                        )
                    }
                }
            }

            SimpleCounter(
                title = "重瓶",
                help = priceText(prices[BottleType.HEAVY] ?: 0),
                qty = heavy,
                minus = { if (heavy > 0) heavy-- },
                plus = { heavy++ }
            )

            MarkCounter(
                title = "租瓶",
                help = "记清租给谁、瓶身厂/检标记",
                marks = marks,
                counts = rentalMarks,
                customer = rentalCustomer,
                onCustomer = { rentalCustomer = it },
                photoLabel = "拍押金单",
                photoPath = rentalImagePath,
                onTakePhoto = { requestTakePhoto("rental") }
            )

            MarkCounter(
                title = "换瓶（对瓶）",
                help = "按瓶身的厂/检标记记数量",
                marks = marks,
                counts = exchangeMarks
            )

            SimpleCounter(
                title = "新瓶",
                help = priceText(prices[BottleType.NEW] ?: 0),
                qty = fresh,
                minus = { if (fresh > 0) fresh-- },
                plus = { fresh++ }
            )
            SimpleCounter(
                title = "小瓶",
                help = priceText(prices[BottleType.SMALL] ?: 0),
                qty = small,
                minus = { if (small > 0) small-- },
                plus = { small++ }
            )

            Title("金额核算")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("瓶子合计", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    MoneyField("增加 +¥", extraText) { extraText = it }
                    MoneyField("减去 -¥", prepayText) { prepayText = it }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("总金额", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("¥" + String.format("%.0f", receivable), fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    }
                    MoneyField("现金", cashText) { cashText = it }
                    if (wechat > 0) {
                        Text(
                            "微信 ¥${String.format("%.0f", wechat)}",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("备注（没有就不填）") },
                minLines = 2
            )

            // 备注拍照（现场照片/单据）
            PhotoSlotButton(
                label = "拍现场照片/单据",
                photoPath = noteImagePath,
                onTakePhoto = { requestTakePhoto("note") }
            )

            Button(
                modifier = Modifier.fillMaxWidth().height(62.dp),
                enabled = !saving,
                onClick = {
                    val selected = employee
                    when {
                        selected == null -> scope.launch { snackbar.showSnackbar("先选择员工") }
                        totalQty == 0 -> scope.launch { snackbar.showSnackbar("还没有记瓶子") }
                        rentalQty > 0 && rentalCustomer.isBlank() -> scope.launch { snackbar.showSnackbar("租瓶要写租给谁") }
                        else -> scope.launch {
                            saving = true
                            try {
                                val notes = mutableListOf<String>()
                                if (heavy > 0) notes += "重瓶: ${heavy}瓶 × ¥${prices[BottleType.HEAVY] ?: 0}"
                                if (rentalQty > 0) notes += "租瓶: ${rentalQty}瓶 (${marksText(rentalMarks)}) [对象: $rentalCustomer]"
                                if (exchangeQty > 0) notes += "对瓶: ${exchangeQty}瓶 (${marksText(exchangeMarks)})"
                                if (fresh > 0) notes += "新瓶: ${fresh}瓶 × ¥${prices[BottleType.NEW] ?: 0}"
                                if (small > 0) notes += "小瓶: ${small}瓶 × ¥${prices[BottleType.SMALL] ?: 0}"
                                if (extra > 0) notes += "增加: ¥${String.format("%.0f", extra)}"
                                if (prepay > 0) notes += "减去: ¥${String.format("%.0f", prepay)}"
                                if (note.isNotBlank()) notes += "备注: $note"

                                val recordId = recordRepo.save(
                                    DeliveryRecord(
                                        employeeId = selected.id,
                                        employeeName = selected.name,
                                        // 必须带上员工的服务器稳定 ID，送气工统计才能按 employeeFirestoreId 正确过滤
                                        employeeFirestoreId = selected.firestoreId,
                                        bottleType = "MIXED",
                                        quantity = totalQty,
                                        pricePerUnit = if (totalQty > 0) total.toDouble() / totalQty else 0.0,
                                        totalAmount = receivable,
                                        cashAmount = cash,
                                        wechatAmount = wechat,
                                        debtAmount = 0.0,
                                        date = System.currentTimeMillis(),
                                        notes = notes.joinToString(" | "),
                                        exchangeStatus = if (exchangeQty > 0) "PENDING" else "NONE",
                                        imagePath = rentalImagePath,
                                        imageUrl = noteImagePath
                                    )
                                )

                                val details = buildBottleDetails(
                                    recordId = recordId,
                                    heavy = heavy,
                                    fresh = fresh,
                                    small = small,
                                    rentalCustomer = rentalCustomer,
                                    rentalMarks = rentalMarks,
                                    exchangeMarks = exchangeMarks,
                                    prices = prices
                                )
                                if (details.isNotEmpty()) db.bottleDetailDao().upsertAll(details)
                                reset()
                                snackbar.showSnackbar("已保存")
                            } catch (e: Exception) {
                                snackbar.showSnackbar("保存失败：${e.message ?: "未知错误"}")
                            } finally {
                                saving = false
                            }
                        }
                    }
                }
            ) {
                Text(if (saving) "正在保存…" else "保存这笔", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("需要相机权限") },
            text = { Text("拍照需要相机权限。请在系统设置中允许本应用使用相机后再试。") },
            confirmButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text("知道了") }
            }
        )
    }
}

@Composable
private fun Title(text: String) {
    Text(text, fontSize = 21.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun SimpleCounter(
    title: String,
    help: String,
    qty: Int,
    minus: () -> Unit,
    plus: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(help)
            }
            FilledTonalIconButton(onClick = minus, enabled = qty > 0) {
                Icon(Icons.Default.Remove, "减1")
            }
            Text(
                qty.toString(),
                modifier = Modifier.widthIn(min = 52.dp),
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold
            )
            FilledIconButton(onClick = plus) {
                Icon(Icons.Default.Add, "加1")
            }
        }
    }
}

@Composable
private fun MarkCounter(
    title: String,
    help: String,
    marks: List<String>,
    counts: MutableMap<String, Int>,
    customer: String? = null,
    onCustomer: (String) -> Unit = {},
    photoLabel: String? = null,
    photoPath: String = "",
    onTakePhoto: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text(help)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${counts.values.sum()} 个", fontWeight = FontWeight.Bold)
                    Text(if (expanded) "▲" else "▼")
                }
            }
            if (expanded) {
                if (customer != null) {
                    OutlinedTextField(
                        value = customer,
                        onValueChange = onCustomer,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("租给谁") },
                        singleLine = true
                    )
                    if (photoLabel != null) {
                        PhotoSlotButton(label = photoLabel, photoPath = photoPath, onTakePhoto = onTakePhoto)
                    }
                }
                if (marks.isEmpty()) {
                    Text("还没有设置厂/检年份，请到设置里添加。")
                }
                marks.forEach { mark ->
                    val qty = counts[mark] ?: 0
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(mark, Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.Medium)
                        FilledTonalIconButton(
                            onClick = { if (qty > 0) counts[mark] = qty - 1 },
                            enabled = qty > 0
                        ) {
                            Icon(Icons.Default.Remove, "减1")
                        }
                        Text(
                            qty.toString(),
                            modifier = Modifier.widthIn(min = 46.dp),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        FilledTonalIconButton(onClick = { counts[mark] = qty + 1 }) {
                            Icon(Icons.Default.Add, "加1")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoSlotButton(
    label: String,
    photoPath: String,
    onTakePhoto: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onTakePhoto, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (photoPath.isNotBlank()) "$label（已拍，可重拍）" else label, fontSize = 17.sp)
            }
            if (photoPath.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AsyncImage(
                        model = File(photoPath),
                        contentDescription = label,
                        modifier = Modifier.size(width = 110.dp, height = 82.dp),
                        contentScale = ContentScale.Crop
                    )
                    Text(
                        File(photoPath).name,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MoneyField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' }) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(fontSize = 20.sp)
    )
}

private fun priceText(price: Int): String = if (price > 0) "¥$price / 瓶" else "价格未设置"

private fun marksText(values: Map<String, Int>): String = values
    .filterValues { it > 0 }
    .entries
    .joinToString(" ") { "${it.key}:${it.value}个" }

private fun buildBottleDetails(
    recordId: Long,
    heavy: Int,
    fresh: Int,
    small: Int,
    rentalCustomer: String,
    rentalMarks: Map<String, Int>,
    exchangeMarks: Map<String, Int>,
    prices: Map<BottleType, Int>
): List<BottleDetail> {
    val result = mutableListOf<BottleDetail>()
    if (heavy > 0) {
        result += BottleDetail(
            deliveryRecordId = recordId,
            bottleType = BottleType.HEAVY.name,
            quantity = heavy,
            unitPrice = (prices[BottleType.HEAVY] ?: 0).toDouble()
        )
    }
    rentalMarks.filterValues { it > 0 }.forEach { (mark, qty) ->
        result += BottleDetail(
            deliveryRecordId = recordId,
            bottleType = BottleType.RENTAL.name,
            quantity = qty,
            productionMark = mark,
            customerName = rentalCustomer,
            unitPrice = (prices[BottleType.RENTAL] ?: 0).toDouble()
        )
    }
    exchangeMarks.filterValues { it > 0 }.forEach { (mark, qty) ->
        result += BottleDetail(
            deliveryRecordId = recordId,
            bottleType = BottleType.EXCHANGE.name,
            quantity = qty,
            productionMark = mark
        )
    }
    if (fresh > 0) {
        result += BottleDetail(
            deliveryRecordId = recordId,
            bottleType = BottleType.NEW.name,
            quantity = fresh,
            unitPrice = (prices[BottleType.NEW] ?: 0).toDouble()
        )
    }
    if (small > 0) {
        result += BottleDetail(
            deliveryRecordId = recordId,
            bottleType = BottleType.SMALL.name,
            quantity = small,
            unitPrice = (prices[BottleType.SMALL] ?: 0).toDouble()
        )
    }
    return result
}
