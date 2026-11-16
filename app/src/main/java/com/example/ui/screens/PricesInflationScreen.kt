package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.BasketItemMap
import com.example.data.model.Product
import com.example.domain.engine.FvgFinancialEngine
import com.example.domain.export.FvgExportService
import com.example.ui.components.*
import com.example.ui.i18n.fvgStrings
import com.example.ui.theme.*
import com.example.ui.viewmodel.FvgUiState
import com.example.ui.viewmodel.FvgViewModel
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PricesInflationScreen(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    isDark: Boolean,
    onToggleLanguage: () -> Unit,
    onToggleTheme: () -> Unit
) {
    val s = fvgStrings(isArabic)
    var subTab by remember { mutableIntStateOf(0) }
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val defaultCurrency = state.activeOrg?.baseCurrency ?: "SDG"

    Column(modifier = Modifier.fillMaxSize().testTag("prices_inflation_screen")) {
        FvgTopHeaderBanner(
            title = if (isArabic) "الأسعار والتضخم والصرف" else "Prices, Inflation & FX",
            subtitle = s.brandTaglineEn,
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            isArabic = isArabic,
            isDark = isDark,
            onToggleLanguage = onToggleLanguage,
            onToggleTheme = onToggleTheme,
            orgName = state.activeOrg?.name,
            roleBadge = state.currentUserRole
        )
        FvgSubTabRow(
            tabs = listOf(s.tabMonitoring, s.tabInflationBaskets, s.tabHistoricalFx),
            selectedIndex = subTab,
            onSelect = { subTab = it },
            testTagPrefix = "prices"
        )

        when (subTab) {
            0 -> PriceMonitoringTab(state, viewModel, isArabic, today, defaultCurrency)
            1 -> InflationBasketsTab(state, viewModel, isArabic, today, defaultCurrency)
            2 -> HistoricalFxTab(state, viewModel, isArabic, today, defaultCurrency)
        }
    }
}

@Composable
private fun PriceMonitoringTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    today: String,
    defaultCurrency: String
) {
    val context = LocalContext.current
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var name by remember { mutableStateOf("") }
    var sku by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Raw Materials") }
    var unit by remember { mutableStateOf("kg") }
    var currency by remember(defaultCurrency) { mutableStateOf(defaultCurrency) }
    var location by remember { mutableStateOf("HQ") }
    var source by remember { mutableStateOf("Supplier Quote") }
    var priceStr by remember { mutableStateOf("") }
    var obsDate by remember { mutableStateOf(today) }
    var notes by remember { mutableStateOf("") }
    var attachmentRef by remember { mutableStateOf("") }

    var searchQuery by remember { mutableStateOf("") }
    var convQty by remember { mutableStateOf("1") }
    var convFrom by remember { mutableStateOf("ton") }
    var convTo by remember { mutableStateOf("kg") }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val mime = context.contentResolver.getType(uri).orEmpty()
                val text = if (mime.contains("spreadsheet") || uri.toString().endsWith(".xlsx", true)) {
                    context.contentResolver.openInputStream(uri)?.use { FvgExportService.extractXlsxToCsvLines(it) }.orEmpty()
                } else {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                }
                viewModel.previewCsvImport(text)
            }
        }
    }

    val filteredObs = remember(state.priceObservations, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) state.priceObservations.reversed()
        else state.priceObservations.filter {
            it.productName.lowercase().contains(q) ||
                it.category.lowercase().contains(q) ||
                it.location.lowercase().contains(q) ||
                it.currency.lowercase().contains(q)
        }.reversed()
    }

    val isDuplicateObservation = remember(state.priceObservations, name, obsDate, priceStr) {
        val pNorm = runCatching { FvgFinancialEngine.money(FvgFinancialEngine.d(priceStr)).toPlainString() }.getOrNull()
        pNorm != null && state.priceObservations.any {
            it.productName.equals(name.trim(), ignoreCase = true) &&
                it.observationDate == obsDate.trim() &&
                it.price == pNorm
        }
    }

    val topIncreases = remember(state.priceObservations) {
        state.priceObservations.filter {
            (runCatching { FvgFinancialEngine.d(it.pctChange) }.getOrNull() ?: BigDecimal.ZERO) > BigDecimal.ZERO
        }.sortedByDescending {
            runCatching { FvgFinancialEngine.d(it.pctChange) }.getOrNull() ?: BigDecimal.ZERO
        }.take(3)
    }

    val topDecreases = remember(state.priceObservations) {
        state.priceObservations.filter {
            (runCatching { FvgFinancialEngine.d(it.pctChange) }.getOrNull() ?: BigDecimal.ZERO) < BigDecimal.ZERO
        }.sortedBy {
            runCatching { FvgFinancialEngine.d(it.pctChange) }.getOrNull() ?: BigDecimal.ZERO
        }.take(3)
    }

    val chartPoints = remember(filteredObs, selectedProduct) {
        val base = if (selectedProduct != null) {
            state.priceObservations.filter { it.productId == selectedProduct!!.id }
        } else {
            filteredObs.reversed()
        }
        base.takeLast(8).mapNotNull { obs ->
            obs.price.toFloatOrNull()?.let { obs.observationDate.takeLast(5) to it }
        }
    }

    val locationStats = remember(state.priceObservations) {
        state.priceObservations.groupBy { it.location.ifBlank { "HQ" } }
            .mapValues { (_, list) ->
                val avg = list.mapNotNull { runCatching { FvgFinancialEngine.d(it.price) }.getOrNull() }
                if (avg.isEmpty()) BigDecimal.ZERO else FvgFinancialEngine.money(
                    avg.fold(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal(avg.size), FvgFinancialEngine.MC)
                )
            }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.priceObservations.isNotEmpty()) {
            item {
                FvgCard(containerColor = FvgSoftCream) {
                    Text(
                        text = if (isArabic) "أعلى الزيادات والانخفاضات ومقارنة الأسواق" else "Top Increases / Decreases & Location Summary",
                        style = MaterialTheme.typography.titleMedium,
                        color = FvgDeepGreen
                    )
                    if (topIncreases.isNotEmpty()) {
                        Text(if (isArabic) "أعلى الارتفاعات:" else "Highest Increases:", style = MaterialTheme.typography.labelLarge, color = FvgNegative)
                        topIncreases.forEach { obs ->
                            val unusual = FvgFinancialEngine.isUnusualPriceMovement(runCatching { FvgFinancialEngine.d(obs.pctChange) }.getOrNull())
                            Text(
                                text = "• ${obs.productName}: +${obs.pctChange}% (${obs.previousPrice} -> ${obs.price} ${obs.currency})" + if (unusual) " [UNUSUAL SPIKE]" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = FvgNegative
                            )
                        }
                    }
                    if (topDecreases.isNotEmpty()) {
                        Text(if (isArabic) "أكبر الانخفاضات:" else "Largest Decreases:", style = MaterialTheme.typography.labelLarge, color = FvgPositive)
                        topDecreases.forEach { obs ->
                            Text("• ${obs.productName}: ${obs.pctChange}% (${obs.previousPrice} -> ${obs.price} ${obs.currency})", style = MaterialTheme.typography.bodyMedium, color = FvgPositive)
                        }
                    }
                    if (locationStats.isNotEmpty()) {
                        Text(
                            text = (if (isArabic) "متوسط الأسعار حسب الموقع: " else "Avg by Location: ") +
                                locationStats.entries.joinToString(" | ") { "${it.key}: ${it.value}" },
                            style = MaterialTheme.typography.labelSmall,
                            color = FvgDeepGreen
                        )
                    }
                }
            }
        }

        if (chartPoints.size >= 2) {
            item {
                FvgCard {
                    Text(
                        text = selectedProduct?.let { "${it.name} Price Trend" }
                            ?: if (isArabic) "منحنى تطور الأسعار التاريخي" else "Historical Price Trend Chart",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FvgSplineLineChartCanvas(points = chartPoints)
                }
            }
        }

        item {
            FvgCard {
                Text(
                    text = if (isArabic) "تسجيل منتج / ملاحظة سعر تاريخية" else "Record Product or New Price Observation",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (state.products.isNotEmpty()) {
                    Text(
                        text = if (isArabic) "اختر منتجاً حالياً لتحديث سعره أو اتركه لإنشاء جديد:" else "Select existing product to record a new historical price (never overwrites history):",
                        style = MaterialTheme.typography.labelSmall
                    )
                    FvgChipSelector(
                        options = listOf("NEW") + state.products.map { it.id },
                        selected = selectedProduct?.id ?: "NEW",
                        onSelect = { id ->
                            val found = state.products.firstOrNull { it.id == id }
                            selectedProduct = found
                            if (found != null) {
                                name = found.name
                                sku = found.sku
                                category = found.category
                                unit = found.unit
                                currency = found.currency
                                location = found.location
                                source = found.source
                            } else {
                                name = ""; sku = ""; priceStr = ""
                            }
                        },
                        labelResolver = { id ->
                            if (id == "NEW") (if (isArabic) "+ منتج جديد" else "+ New Product")
                            else state.products.firstOrNull { it.id == id }?.name ?: id
                        }
                    )
                }

                FvgField(name, { name = it }, if (isArabic) "اسم المنتج / المادة" else "Product / Material Name", testTag = "price_product_name_input")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(sku, { sku = it }, "SKU", modifier = Modifier.weight(1f))
                    FvgField(category, { category = it }, if (isArabic) "الفئة" else "Category", modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(unit, { unit = it }, if (isArabic) "الوحدة (kg/ton)" else "Unit (kg/ton)", modifier = Modifier.weight(1f))
                    FvgField(currency, { currency = it.uppercase() }, if (isArabic) "العملة" else "Currency", modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(priceStr, { priceStr = it }, if (isArabic) "السعر الجديد" else "New Price", numeric = true, modifier = Modifier.weight(1f), testTag = "price_value_input")
                    FvgField(obsDate, { obsDate = it }, if (isArabic) "التاريخ" else "Date (YYYY-MM-DD)", modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(location, { location = it }, if (isArabic) "الموقع/السوق" else "Location", modifier = Modifier.weight(1f))
                    FvgField(source, { source = it }, if (isArabic) "المصدر/المورد" else "Source/Supplier", modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(attachmentRef, { attachmentRef = it }, if (isArabic) "مرجع الفاتورة / الوثيقة" else "Invoice / Receipt Ref", modifier = Modifier.weight(1f))
                    FvgField(notes, { notes = it }, if (isArabic) "ملاحظات" else "Notes", modifier = Modifier.weight(1f))
                }

                val previewCalc = remember(priceStr, selectedProduct) {
                    val p = runCatching { FvgFinancialEngine.d(priceStr) }.getOrNull()
                    val old = selectedProduct?.let { FvgFinancialEngine.dOrZero(it.currentPrice) } ?: p
                    if (p != null && old != null) runCatching { FvgFinancialEngine.calculatePriceChange(p, old) }.getOrNull() else null
                }
                if (previewCalc != null) {
                    val unusual = FvgFinancialEngine.isUnusualPriceMovement(previewCalc.pctChange)
                    Surface(shape = RoundedCornerShape(12.dp), color = if (unusual) FvgNegative.copy(alpha = 0.12f) else FvgSoftCream, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Delta: ${previewCalc.absChange} (${previewCalc.pctChange ?: "N/A"}% ${previewCalc.direction})" +
                                if (unusual) "  ⚠ Unusual Price Movement (>=15%)" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (unusual) FvgNegative else FvgDeepGreen,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                if (isDuplicateObservation) {
                    Text(
                        text = if (isArabic) "تنبيه: توجد ملاحظة مطابقة لنفس المنتج والتاريخ والسعر بالفعل." else "Duplicate detected: Same product, date, and price already recorded.",
                        style = MaterialTheme.typography.labelSmall,
                        color = FvgNegative
                    )
                }

                FvgPrimaryButton(
                    text = if (isArabic) "حفظ السعر وحساب التغير" else "Save Price Observation",
                    icon = Icons.Filled.Save,
                    enabled = state.canEditFinancialData && name.isNotBlank() && priceStr.isNotBlank() && !isDuplicateObservation && !state.isBusy,
                    onClick = {
                        viewModel.recordPriceObservation(
                            selectedProduct, name, sku, category, "", unit,
                            currency, location, source, priceStr, obsDate, notes, attachmentRef
                        )
                    },
                    testTag = "save_price_button"
                )
            }
        }

        item {
            FvgCard {
                Text(
                    text = if (isArabic) "محول الوحدات واستيراد CSV / XLSX الآمن" else "Unit Converter & Sanitized CSV / XLSX Import",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FvgField(convQty, { convQty = it }, if (isArabic) "الكمية" else "Qty", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(convFrom, { convFrom = it }, if (isArabic) "من" else "From", modifier = Modifier.weight(1f))
                    FvgField(convTo, { convTo = it }, if (isArabic) "إلى" else "To", modifier = Modifier.weight(1f))
                }
                val converted = remember(convQty, convFrom, convTo) {
                    runCatching { FvgFinancialEngine.convertUnits(FvgFinancialEngine.d(convQty), convFrom, convTo).toPlainString() }
                        .getOrElse { it.message ?: "Incompatible units" }
                }
                Text(
                    text = if (isArabic) "النتيجة: $converted $convTo" else "Converted: $converted $convTo",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FvgForestGreen
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { csvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "*/*")) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isArabic) "استيراد ملف" else "Pick CSV / XLSX")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.previewCsvImport(
                                "name,sku,category,unit,currency,price,location\nWheat Flour,WHT-50,Grain,bag,SDG,38500.00,Khartoum\nPackaging Box,BOX-10,Packaging,box,SDG,12000.00,HQ"
                            )
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text(if (isArabic) "معاينة نموذج آمن" else "Preview Template")
                    }
                }

                if (state.csvPreviewRows.isNotEmpty()) {
                    Text(
                        text = if (isArabic) "معاينة الاستيراد (يتم رفض أي خلايا صيغ =,+,-,@):" else "Import Preview (Formula injection & duplicate SKUs guarded):",
                        style = MaterialTheme.typography.labelLarge
                    )
                    state.csvPreviewRows.take(6).forEach { r ->
                        val ok = r.error == null
                        Text(
                            text = if (ok) "✓ Row ${r.rowNumber}: ${r.name} (${r.sku}) = ${r.currentPrice} ${r.currency}" else "✗ ${r.error}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (ok) FvgPositive else FvgNegative
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgGoldButton(
                            text = if (isArabic) "اعتماد الصفوف الصالحة" else "Commit Valid Rows",
                            enabled = state.canEditFinancialData && state.csvPreviewRows.any { it.error == null },
                            onClick = { viewModel.commitCsvImport(today) },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = { viewModel.clearCsvPreview() }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(if (isArabic) "مسح" else "Clear")
                        }
                    }
                }
            }
        }

        item {
            FvgField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = if (isArabic) "بحث بالاسم أو الفئة أو الموقع أو العملة" else "Filter history by product, category, location, currency"
            )
        }

        if (filteredObs.isEmpty()) {
            item { FvgEmptyStateCard(fvgStrings(isArabic).emptyNoRecords) }
        } else {
            items(filteredObs, key = { it.id }) { obs ->
                val unusual = FvgFinancialEngine.isUnusualPriceMovement(runCatching { FvgFinancialEngine.d(obs.pctChange) }.getOrNull())
                FvgCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(obs.productName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${obs.category} • ${obs.unit} • ${obs.location} • ${obs.observationDate}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = when (obs.direction) {
                                "increased" -> FvgNegative.copy(alpha = 0.14f)
                                "decreased" -> FvgPositive.copy(alpha = 0.14f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = "${obs.pctChange}%" + if (unusual) " !" else "",
                                style = MaterialTheme.typography.labelLarge,
                                color = when (obs.direction) {
                                    "increased" -> FvgNegative
                                    "decreased" -> FvgPositive
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Text(
                        text = "${obs.previousPrice} -> ${obs.price} ${obs.currency} (Δ ${obs.absChange} • ${obs.source})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (obs.attachmentRef.isNotBlank()) {
                        Text("Ref: ${obs.attachmentRef}", style = MaterialTheme.typography.labelSmall, color = FvgAntiqueGold)
                    }
                }
            }
        }
    }
}

@Composable
private fun InflationBasketsTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    today: String,
    defaultCurrency: String
) {
    var period by remember { mutableStateOf(today.take(7)) }
    var category by remember { mutableStateOf("All Items") }
    var monthlyRate by remember { mutableStateOf("") }
    var annualRate by remember { mutableStateOf("") }
    var indexVal by remember { mutableStateOf("100.00") }
    var basePeriod by remember { mutableStateOf("2025-01") }
    var sourceType by remember { mutableStateOf("official") }
    var sourceName by remember { mutableStateOf("Central Bureau of Statistics") }
    var formulaNote by remember { mutableStateOf("((Current Index - Previous Index) / Previous Index) × 100") }

    var basketName by remember { mutableStateOf("Core Operating Basket") }
    var item1Name by remember { mutableStateOf("Wheat Flour") }
    var item1Qty by remember { mutableStateOf("10") }
    var item1Weight by remember { mutableStateOf("0.6") }
    var item1Base by remember { mutableStateOf("30.00") }
    var item1Curr by remember { mutableStateOf("36.00") }

    var item2Name by remember { mutableStateOf("Diesel Fuel") }
    var item2Qty by remember { mutableStateOf("20") }
    var item2Weight by remember { mutableStateOf("0.4") }
    var item2Base by remember { mutableStateOf("1.20") }
    var item2Curr by remember { mutableStateOf("1.50") }

    val cumulativePct = remember(state.inflationRecords) {
        val rates = state.inflationRecords.mapNotNull { runCatching { FvgFinancialEngine.d(it.monthlyRatePct) }.getOrNull() }
        if (rates.isEmpty()) "0.00" else FvgFinancialEngine.calculateCumulativeInflationPct(rates).toPlainString()
    }

    val liveBasketCalc = remember(item1Name, item1Qty, item1Weight, item1Base, item1Curr, item2Name, item2Qty, item2Weight, item2Base, item2Curr) {
        runCatching {
            FvgFinancialEngine.calculateWeightedBasket(
                listOf(
                    FvgFinancialEngine.BasketLineInput(item1Name, FvgFinancialEngine.d(item1Qty), FvgFinancialEngine.d(item1Weight), FvgFinancialEngine.d(item1Base), FvgFinancialEngine.d(item1Curr)),
                    FvgFinancialEngine.BasketLineInput(item2Name, FvgFinancialEngine.d(item2Qty), FvgFinancialEngine.d(item2Weight), FvgFinancialEngine.d(item2Base), FvgFinancialEngine.d(item2Curr))
                )
            )
        }.getOrNull()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FvgCard(containerColor = FvgSoftCream) {
                Text(
                    text = if (isArabic) "التضخم التراكمي المسجل: $cumulativePct%" else "Cumulative Compounded Inflation: $cumulativePct%",
                    style = MaterialTheme.typography.titleMedium,
                    color = FvgDeepGreen
                )
                Text(
                    text = "Formula: Π(1 + rᵢ/100) - 1  •  Records: ${state.inflationRecords.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = FvgMutedForest
                )
            }
        }

        item {
            FvgCard {
                Text(
                    text = if (isArabic) "تسجيل مؤشر ومعدل تضخم" else "Record Monthly / Annual Inflation Index",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                FvgChipSelector(
                    options = listOf("official", "calculated", "manual", "provisional"),
                    selected = sourceType,
                    onSelect = { sourceType = it }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(period, { period = it }, "Period (YYYY-MM)", modifier = Modifier.weight(1f))
                    FvgField(basePeriod, { basePeriod = it }, "Base Period", modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(monthlyRate, { monthlyRate = it }, "Monthly Rate %", numeric = true, modifier = Modifier.weight(1f), testTag = "inflation_monthly_input")
                    FvgField(annualRate, { annualRate = it }, "Annual Rate %", numeric = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(indexVal, { indexVal = it }, "CPI / Price Index", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(category, { category = it }, "Category", modifier = Modifier.weight(1f))
                }
                FvgField(sourceName, { sourceName = it }, "Source Name")
                FvgField(formulaNote, { formulaNote = it }, "Formula / Assumption Note")
                FvgPrimaryButton(
                    text = if (isArabic) "حفظ سجل التضخم" else "Save Inflation Record",
                    enabled = state.canEditFinancialData && period.isNotBlank() && monthlyRate.isNotBlank() && !state.isBusy,
                    onClick = {
                        viewModel.saveInflation(period, category, monthlyRate, annualRate, indexVal, basePeriod, sourceType, sourceName, formulaNote)
                    },
                    testTag = "save_inflation_button"
                )
            }
        }

        item {
            FvgCard {
                Text(
                    text = if (isArabic) "حاسبة وسلة الأسعار المرجحة" else "Weighted Price Basket Builder",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                FvgField(basketName, { basketName = it }, "Basket Name")
                Text("Item 1", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FvgField(item1Name, { item1Name = it }, "Name", modifier = Modifier.weight(1.5f))
                    FvgField(item1Qty, { item1Qty = it }, "Qty", numeric = true, modifier = Modifier.weight(0.8f))
                    FvgField(item1Weight, { item1Weight = it }, "Wt", numeric = true, modifier = Modifier.weight(0.8f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FvgField(item1Base, { item1Base = it }, "Base Price", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(item1Curr, { item1Curr = it }, "Current Price", numeric = true, modifier = Modifier.weight(1f))
                }
                Text("Item 2", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FvgField(item2Name, { item2Name = it }, "Name", modifier = Modifier.weight(1.5f))
                    FvgField(item2Qty, { item2Qty = it }, "Qty", numeric = true, modifier = Modifier.weight(0.8f))
                    FvgField(item2Weight, { item2Weight = it }, "Wt", numeric = true, modifier = Modifier.weight(0.8f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FvgField(item2Base, { item2Base = it }, "Base Price", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(item2Curr, { item2Curr = it }, "Current Price", numeric = true, modifier = Modifier.weight(1f))
                }
                if (liveBasketCalc != null && liveBasketCalc.breakdown.isNotEmpty()) {
                    FvgBarComparisonCanvas(
                        bars = liveBasketCalc.breakdown.map { it.name.take(10) to it.currentContribution.toFloat() }
                    )
                    Text(
                        text = "Basket Index: ${liveBasketCalc.indexValue ?: "N/A"} | Base: ${liveBasketCalc.baseCost} -> Current: ${liveBasketCalc.currentCost} $defaultCurrency",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = FvgDeepGreen
                    )
                }
                FvgGoldButton(
                    text = if (isArabic) "حفظ السلة وحساب المؤشر" else "Compute & Save Basket Snapshot",
                    enabled = state.canEditFinancialData && basketName.isNotBlank() && !state.isBusy,
                    onClick = {
                        val items = listOf(
                            BasketItemMap(item1Name, "unit", item1Qty, item1Weight, item1Base, item1Curr),
                            BasketItemMap(item2Name, "unit", item2Qty, item2Weight, item2Base, item2Curr)
                        )
                        viewModel.saveBasket(null, basketName, basePeriod, period, defaultCurrency, items)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (state.priceBaskets.size >= 2) {
            item {
                FvgCard {
                    Text(if (isArabic) "مقارنة السلات المرجحة" else "Multi-Basket Index Comparison", style = MaterialTheme.typography.titleMedium)
                    FvgBarComparisonCanvas(
                        bars = state.priceBaskets.take(5).map { b ->
                            b.name.take(10) to (b.indexValue.toFloatOrNull() ?: 100f)
                        }
                    )
                }
            }
        }

        items(state.priceBaskets, key = { it.id }) { bsk ->
            FvgCard {
                Text("${bsk.name} (v${bsk.version})", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Base (${bsk.basePeriod}): ${bsk.baseCost} ${bsk.currency} -> Current (${bsk.currentPeriod}): ${bsk.currentCost} ${bsk.currency} | Index: ${bsk.indexValue}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FvgForestGreen
                )
            }
        }

        items(state.inflationRecords, key = { it.id }) { rec ->
            FvgCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${rec.period} • ${rec.category}", style = MaterialTheme.typography.titleMedium)
                    FvgRoleBadge(rec.sourceType)
                }
                Text("Monthly: ${rec.monthlyRatePct}% | Index: ${rec.indexValue} (Base ${rec.basePeriod})", style = MaterialTheme.typography.bodyMedium)
                Text("${rec.sourceName} — ${rec.formulaNote}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HistoricalFxTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    today: String,
    defaultCurrency: String
) {
    var baseCurr by remember { mutableStateOf("USD") }
    var quoteCurr by remember(defaultCurrency) { mutableStateOf(if (defaultCurrency == "USD") "SDG" else defaultCurrency) }
    var rateStr by remember { mutableStateOf("") }
    var effDate by remember { mutableStateOf(today) }
    var rateType by remember { mutableStateOf("official") }
    var sourceName by remember { mutableStateOf("Central Bank") }
    var convertAmount by remember { mutableStateOf("100") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FvgCard {
                Text(
                    text = if (isArabic) "سجل أسعار الصرف التاريخية" else "Record Historical Exchange Rate",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                FvgChipSelector(
                    options = listOf("official", "market", "manual"),
                    selected = rateType,
                    onSelect = { rateType = it }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(baseCurr, { baseCurr = it.uppercase() }, "Base (e.g. USD)", modifier = Modifier.weight(1f))
                    FvgField(quoteCurr, { quoteCurr = it.uppercase() }, "Quote (e.g. SDG)", modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(rateStr, { rateStr = it }, "Rate (1 Base = ? Quote)", numeric = true, modifier = Modifier.weight(1f), testTag = "fx_rate_input")
                    FvgField(effDate, { effDate = it }, "Effective Date", modifier = Modifier.weight(1f))
                }
                FvgField(sourceName, { sourceName = it }, "Rate Source")
                FvgPrimaryButton(
                    text = if (isArabic) "حفظ سعر الصرف التاريخي" else "Save Historical FX Rate",
                    enabled = state.canEditFinancialData && rateStr.isNotBlank() && !state.isBusy,
                    onClick = { viewModel.saveFxRate(baseCurr, quoteCurr, rateStr, effDate, rateType, sourceName, "") },
                    testTag = "save_fx_button"
                )
            }
        }

        item {
            FvgCard(containerColor = FvgSoftCream) {
                Text(
                    text = if (isArabic) "محول العملات بالسعر التاريخي المسجل" else "Historical Rate Converter",
                    style = MaterialTheme.typography.titleMedium,
                    color = FvgDeepGreen
                )
                FvgField(convertAmount, { convertAmount = it }, "Amount", numeric = true)
                val latestRate = state.fxRates.firstOrNull()
                if (latestRate == null) {
                    Text(
                        text = if (isArabic) "سجل سعر صرف أولاً لتطبيق التحويل التاريخي." else "Record a historical FX rate above to convert without fabricated rates.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    val result = runCatching {
                        FvgFinancialEngine.convertCurrency(FvgFinancialEngine.d(convertAmount), FvgFinancialEngine.d(latestRate.rate))
                    }.getOrNull()
                    Text(
                        text = "$convertAmount ${latestRate.baseCurrency} = ${result ?: "N/A"} ${latestRate.quoteCurrency} @ ${latestRate.rate} (${latestRate.rateType}, ${latestRate.effectiveDate})",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = FvgDeepGreen
                    )
                }
            }
        }

        if (state.fxRates.size >= 2) {
            item {
                FvgCard {
                    Text(if (isArabic) "مقارنة أسعار الصرف التاريخية" else "FX Official vs Market Rate Trend", style = MaterialTheme.typography.titleMedium)
                    FvgSplineLineChartCanvas(
                        points = state.fxRates.take(8).mapNotNull { fx ->
                            fx.rate.toFloatOrNull()?.let { "${fx.effectiveDate.takeLast(5)}(${fx.rateType.take(3)})" to it }
                        }
                    )
                }
            }
        }

        items(state.fxRates, key = { it.id }) { fx ->
            FvgCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("1 ${fx.baseCurrency} = ${fx.rate} ${fx.quoteCurrency}", style = MaterialTheme.typography.titleMedium)
                    FvgRoleBadge(fx.rateType)
                }
                Text("Effective: ${fx.effectiveDate} • Source: ${fx.sourceName}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
