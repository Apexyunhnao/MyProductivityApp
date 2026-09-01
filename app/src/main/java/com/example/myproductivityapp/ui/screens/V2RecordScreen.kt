package com.example.myproductivityapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myproductivityapp.MainActivity
import com.example.myproductivityapp.data.AppDatabase
import com.example.myproductivityapp.data.model.*
import com.example.myproductivityapp.data.repository.BottleYearRepository
import com.example.myproductivityapp.data.repository.DeliveryRecordRepository
import com.example.myproductivityapp.data.repository.EmployeeRepository
import com.example.myproductivityapp.data.repository.PriceConfigRepository
import kotlinx.coroutines.launch

/**
 * V2 高频记账页。
 * 目标：一屏完成“选员工 -> 记瓶 -> 记钱 -> 保存”，不再走旧版 4 步向导。
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var selectedEmployee by remember { mutableStateOf<Employee?>(null) }
    var availableYears by remember { mutableStateOf<List<BottleYear>>(emptyList()) }
    var prices by remember { mutableStateOf<Map<BottleType, Int>>(emptyMap()) }

    var heavyQty by remember { mutableIntStateOf(0) }
    var newQty by remember { mutableIntStateOf(0) }
    var smallQty by remember { mutableIntStateOf(0) }
    val rentalMarks = remember { mutableStateMapOf<String, Int>() }
    val exchangeMarks = remember { mutableStateMapOf<String, Int>() }
    var rentalCustomer by remember { mutableStateOf("") }

    var cashText by remember { mutableStateOf("") }
    var wechatText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        employeeRepo.observeAll().collect { list ->
            employees = list
            if (selectedEmployee == null && list.isNotEmpty()) selectedEmployee = list.first()
        }
    }
    LaunchedEffect(Unit) {
        yearRepo.observeAll().collect { list ->
            availableYears = list.distinctBy { "${it.year}-${it.type}" }
        }
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
    val totalQty = heavyQty + rentalQty + exchangeQty + newQty + smallQty
    val totalAmount =
        heavyQty * (prices[BottleType.HEAVY] ?: 0) +
        rentalQty * (prices[BottleType.RENTAL] ?: 0) +
        newQty * (prices[BottleType.NEW] ?: 0) +
        smallQty * (prices[BottleType.SMALL] ?: 0)

    val cash = cashText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val wechat = wechatText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val debt = (totalAmount - cash - wechat).coerceAtLeast(0.0)

    fun resetForm() {
        heavyQty = 0
        newQty = 0
        smallQty = 0
        rentalMarks.clear()
        exchangeMarks.clear()
        rentalCustomer = ""
        cashText = ""
        wechatText = ""
        note = ""
    }

    fun markText(map: Map<String, Int>): String = map
        .filterValues { it > 0 }
        .entries
        .joinToString(" ") { "${it.key}:${it.value}个" }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("记一笔", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("先点谁送的，再记数量和收款。", style = MaterialTheme.typography.bodyLarge)

            SectionTitle("1  谁送的")
            if (employees.isEmpty()) {
                AssistChip(onClick = {}, label = { Text("还没有员工，请先到设置添加") })
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(employees, key = { it.id }) { employee ->
                        FilterChip(
                            selected = selectedEmployee?.id == employee.id,
                            onClick = { selectedEmployee = employee },
                            label = { Text(employee.name, fontSize = 18.sp) }
                        )
                    }
                }
            }

            SectionTitle("2  瓶子")
            BottleCounterCard(
                title = "重瓶",
                subtitle = priceLabel(prices[BottleType.HEAVY] ?: 0),
                quantity = heavyQty,
                onMinus = { if (heavyQty > 0) heavyQty-- },
                onPlus = { heavyQty++ }
            )

            MarkedBottleCard(
                title = "租瓶",
                subtitle = "要记清租给谁、瓶身厂/检标记",
                marks = availableYears.map { "${it.year}${it.type}" },
                counts = rentalMarks,
                customerName = rentalCustomer,
                showCustomer = true,
                onCustomerChange = { rentalCustomer = it }
            )

            MarkedBottleCard(
                title = "换瓶（对瓶）",
                subtitle = "按瓶身的厂/检标记记数量",
                marks = availableYears.map { "${it.year}${it.type}" },
                counts = exchangeMarks,
                customerName = "",
                showCustomer = false,
                onCustomerChange = {}
            )

            BottleCounterCard(
                title = "新瓶",
                subtitle = priceLabel(prices[BottleType.NEW] ?: 0),
                quantity = newQty,
                onMinus = { if (newQty > 0) newQty-- },
                onPlus = { newQty++ }
            )

            BottleCounterCard(
                title = "小瓶",
                subtitle = priceLabel(prices[BottleType.SMALL] ?: 0),
                quantity = smallQty,
                onMinus = { if (smallQty > 0) smallQty-- },
                onPlus = { smallQty++ }
            )

            SectionTitle("3  收了多少钱")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("合计", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("¥$totalAmount", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedTextField(
                        value = cashText,
                        onValueChange = { cashText = it.filter { c -> c.isDigit() || c == '.' } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("现金") },
                        textStyle = LocalTextStyle.current.copy(fontSize = 20.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = wechatText,
                        onValueChange = { wechatText = it.filter { c -> c.isDigit() || c == '.' } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("微信") },
                        textStyle = LocalTextStyle.current.copy(fontSize = 20.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    if (debt > 0) {
                        Text(
                            "还欠：¥${String.format("%.2f", debt)}",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (cash + wechat > 0) {
                        Text("已结清", color = MaterialTheme.colorScheme.primary, fontSize = 19.sp)
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

            Button(
                onClick = {
                    val employee = selectedEmployee
                    when {
                        employee == null -> scope.launch { snackbarHostState.showSnackbar("先选择员工") }
                        totalQty <= 0 -> scope.launch { snackbarHostState.showSnackbar("还没有记瓶子数量") }
                        rentalQty > 0 && rentalCustomer.isBlank() -> scope.launch { snackbarHostState.showSnackbar("租瓶要填写租给谁") }
                        saving -> Unit
                        else -> {
                            saving = true
                            scope.launch {
                                try {
                                    val legacyParts = mutableListOf<String>()
                                    if (heavyQty > 0) legacyParts += "重瓶: ${heavyQty}瓶 × ¥${prices[BottleType.HEAVY] ?: 0}"
                                    if (rentalQty > 0) legacyParts += "租瓶: ${rentalQty}瓶 (${markText(rentalMarks)}) [对象: $rentalCustomer]"
                                    if (exchangeQty > 0) legacyParts += "对瓶: ${exchangeQty}瓶 (${markText(exchangeMarks)})"
                                    if (newQty > 0) legacyParts += "新瓶: ${newQty}瓶 × ¥${prices[BottleType.NEW] ?: 0}"
                                    if (smallQty > 0) legacyParts += "小瓶: ${smallQty}瓶 × ¥${prices[BottleType.SMALL] ?: 0}"
                                    if (note.isNotBlank()) legacyParts += "备注: $note"

                                    val record = DeliveryRecord(
                                        employeeId = employee.id,
                                        employeeName = employee.name,
                                        bottleType = "MIXED",
                                        quantity = totalQty,
                                        pricePerUnit = if (totalQty > 0) totalAmount.toDouble() / totalQty else 0.0,
                                        totalAmount = totalAmount.toDouble(),
                                        cashAmount = cash,
                                        wechatAmount = wechat,
                                        debtAmount = debt,
                                        date = System.currentTimeMillis(),
                                        notes = legacyParts.joinToString(" | "),
                                        exchangeStatus = if (exchangeQty > 0) "PENDING" else "NONE"
                                    )
                                    val recordId = recordRepo.save(record)

                                    val details = mutableListOf<BottleDetail>()
                                    if (heavyQty > 0) details += BottleDetail(
                                        deliveryRecordId = recordId,
                                        bottleType = BottleType.HEAVY.name,
                                        quantity = heavyQty,
                                        unitPrice = (prices[BottleType.HEAVY] ?: 0).toDouble()
                                    )
                                    rentalMarks.filterValues { it > 0 }.forEach { (mark, qty) ->
                                        details += BottleDetail(
                                            deliveryRecordId = recordId,
                                            bottleType = BottleType.RENTAL.name,
                                            quantity = qty,
                                            productionMark = mark,
                                            customerName = rentalCustomer,
                                            unitPrice = (prices[BottleType.RENTAL] ?: 0).toDouble()
                                        )
                                    }
                                    exchangeMarks.filterValues { it > 0 }.forEach { (mark, qty) ->
                                        details += BottleDetail(
                                            deliveryRecordId = recordId,
                                            bottleType = BottleType.EXCHANGE.name,
                                            quantity = qty,
                                            productionMark = mark
                                        )
                                    }
                                    if (newQty > 0) details += BottleDetail(
                                        deliveryRecordId = recordId,
                                        bottleType = BottleType.NEW.name,
                                        quantity = newQty,
                                        unitPrice = (prices[BottleType.NEW] ?: 0).toDouble()
                                    )
                                    if (smallQty > 0) details += BottleDetail(
                                        deliveryRecordId = recordId,
                                        bottleType = BottleType.SMALL.name,
                                        quantity = smallQty,
                                        unitPrice = (prices[BottleType.SMALL] ?: 0).toDouble()
                                    )
                                    if (details.isNotEmpty()) db.bottleDetailDao().upsertAll(details)

                                    resetForm()
                                    snackbarHostState.showSnackbar("已保存")
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("保存失败：${e.message ?: "未知错误"}")
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp),
                enabled = !saving
            ) {
                Text(if (saving) "正在保存…" else "保存这笔", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 21.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun BottleCounterCard(
    title: String,
    subtitle: String,
    quantity: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            FilledTonalIconButton(onClick = onMinus, enabled = quantity > 0, modifier = Modifier.size(50.dp)) {
                Icon(Icons.Default.Remove, contentDescription = "减1")
            }
            Text(
                quantity.toString(),
                modifier = Modifier.widthIn(min = 54.dp),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            FilledIconButton(onClick = onPlus, modifier = Modifier.size(50.dp)) {
                Icon(Icons.Default.Add, contentDescription = "加1")
            }
        }
    }
}

@Composable
private fun MarkedBottleCard(
    title: String,
    subtitle: String,
    marks: List<String>,
    counts: MutableMap<String, Int>,
    customerName: String,
    showCustomer: Boolean,
    onCustomerChange: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            if (showCustomer) {
                OutlinedTextField(
                    value = customerName,
                    onValueChange = onCustomerChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("租给谁") },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 19.sp)
                )
            }
            if (marks.isEmpty()) {
                Text("还没有设置厂/检年份，请到设置里添加。")
            } else {
                marks.distinct().forEach { mark ->
                    val qty = counts[mark] ?: 0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(mark, modifier = Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.Medium)
                        FilledTonalIconButton(
                            onClick = { if (qty > 0) counts[mark] = qty - 1 },
                            enabled = qty > 0,
                            modifier = Modifier.size(44.dp)
                        ) { Icon(Icons.Default.Remove, contentDescription = "减1") }
                        Text(qty.toString(), modifier = Modifier.widthIn(min = 48.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        FilledTonalIconButton(
                            onClick = { counts[mark] = qty + 1 },
                            modifier = Modifier.size(44.dp)
                        ) { Icon(Icons.Default.Add, contentDescription = "加1") }
                    }
                }
            }
        }
    }
}

private fun priceLabel(price: Int): String = if (price > 0) "¥$price / 瓶" else "价格未设置"
