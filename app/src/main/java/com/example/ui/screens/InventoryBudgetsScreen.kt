package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.InventoryItemRecord
import com.example.domain.engine.FvgFinancialEngine
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
fun InventoryBudgetsScreen(
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

    Column(modifier = Modifier.fillMaxSize().testTag("inventory_budgets_screen")) {
        FvgTopHeaderBanner(
            title = if (isArabic) "المخزون والجرد والهدر والموازنات" else "Inventory, Scrap & Budgets",
            subtitle = s.brandTaglineEn,
            icon = Icons.Filled.Inventory2,
            isArabic = isArabic,
            isDark = isDark,
            onToggleLanguage = onToggleLanguage,
            onToggleTheme = onToggleTheme,
            orgName = state.activeOrg?.name,
            roleBadge = state.currentUserRole
        )
        FvgSubTabRow(
            tabs = listOf(s.tabInventoryValuation, s.tabStocktaking, s.tabScrapLosses, s.tabBudgets),
            selectedIndex = subTab,
            onSelect = { subTab = it },
            testTagPrefix = "inventory"
        )

        when (subTab) {
            0 -> InventoryValuationTab(state, viewModel, isArabic, today, defaultCurrency)
            1 -> StocktakingTab(state, viewModel, isArabic, today)
            2 -> ScrapLossesTab(state, viewModel, isArabic, today, defaultCurrency)
            3 -> BudgetsTab(state, viewModel, isArabic, today, defaultCurrency)
        }
    }
}

@Composable
private fun InventoryValuationTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    today: String,
    defaultCurrency: String
) {
    var sku by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var itemType by remember { mutableStateOf("raw_material") }
    var unit by remember { mutableStateOf("kg") }
    var warehouse by remember { mutableStateOf("Main Warehouse") }
    var method by remember { mutableStateOf("fifo") }
    var openQty by remember { mutableStateOf("100") }
    var openCost by remember { mutableStateOf("2.50") }
    var marketPrice by remember { mutableStateOf("3.00") }

    var selectedItem by remember(state.inventoryItems) { mutableStateOf<InventoryItemRecord?>(state.inventoryItems.firstOrNull()) }
    var moveType by remember { mutableStateOf("receipt") }
    var moveQty by remember { mutableStateOf("") }
    var moveCost by remember { mutableStateOf("") }
    var moveReason by remember { mutableStateOf("Standard stock movement") }
    var postAutoJe by remember { mutableStateOf(true) }

    val latestInfRate = state.inflationRecords.firstOrNull()?.monthlyRatePct?.let {
        runCatching { BigDecimal.ONE.add(FvgFinancialEngine.d(it).divide(BigDecimal(100))) }.getOrNull()
    } ?: BigDecimal.ONE

    val replacementTotals = remember(state.inventoryItems, latestInfRate) {
        FvgFinancialEngine.analyzeReplacementCost(
            quantityOnHand = BigDecimal.ONE,
            bookValue = state.totalInventoryBookValue,
            currentMarketPrice = state.totalReplacementCost,
            inflationFactor = latestInfRate
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FvgCard(containerColor = FvgSoftCream) {
                Text(
                    text = if (isArabic) "القيمة الدفترية مقابل تكلفة الاستبدال والتضخم" else "Inventory Book Value vs Replacement Cost (Separate from Ledger)",
                    style = MaterialTheme.typography.titleMedium,
                    color = FvgDeepGreen
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (isArabic) "القيمة الدفترية (FIFO/MWA)" else "Accounting Book Value:", color = FvgInkDark)
                    Text("${replacementTotals.bookValue} $defaultCurrency", fontWeight = FontWeight.Bold, color = FvgDeepGreen)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (isArabic) "تكلفة الاستبدال الحالية" else "Current Replacement Cost:", color = FvgInkDark)
                    Text("${replacementTotals.currentReplacementCost} $defaultCurrency", fontWeight = FontWeight.Bold, color = FvgAntiqueGold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (isArabic) "القيمة المعدلة بالتضخم" else "Inflation-Adjusted Value:", color = FvgInkDark)
                    Text("${replacementTotals.inflationAdjustedCost} $defaultCurrency", fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (isArabic) "فجوة رأس المال المطلوب للاستبدال" else "Replacement Capital Gap:", color = FvgInkDark)
                    Text(
                        "${replacementTotals.replacementGap} $defaultCurrency (${replacementTotals.capitalTiedUpPctIncrease ?: "0.00"}%)",
                        fontWeight = FontWeight.Bold,
                        color = if (replacementTotals.replacementGap > BigDecimal.ZERO) FvgNegative else FvgPositive
                    )
                }
                if (state.inventoryItems.isNotEmpty()) {
                    FvgBarComparisonCanvas(
                        bars = listOf(
                            "Book Value" to replacementTotals.bookValue.toFloat(),
                            "Replacement" to replacementTotals.currentReplacementCost.toFloat(),
                            "Infl. Adj" to replacementTotals.inflationAdjustedCost.toFloat()
                        )
                    )
                }
            }
        }

        item {
            FvgCard {
                Text(if (isArabic) "تسجيل صنف مخزني جديد" else "Register Inventory Item", style = MaterialTheme.typography.titleMedium)
                FvgChipSelector(listOf("raw_material", "finished_good"), itemType, { itemType = it })
                FvgChipSelector(listOf("fifo", "moving_avg"), method, { method = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(sku, { sku = it }, "SKU", modifier = Modifier.weight(0.8f), testTag = "inv_sku_input")
                    FvgField(name, { name = it }, "Item Name", modifier = Modifier.weight(1.4f), testTag = "inv_name_input")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(unit, { unit = it }, "Unit", modifier = Modifier.weight(0.8f))
                    FvgField(warehouse, { warehouse = it }, "Warehouse", modifier = Modifier.weight(1.2f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(openQty, { openQty = it }, "Opening Qty", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(openCost, { openCost = it }, "Unit Cost", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(marketPrice, { marketPrice = it }, "Market Price", numeric = true, modifier = Modifier.weight(1f))
                }
                FvgPrimaryButton(
                    text = if (isArabic) "إضافة الصنف" else "Create Inventory Item",
                    enabled = state.canEditFinancialData && sku.isNotBlank() && name.isNotBlank() && !state.isBusy,
                    onClick = {
                        viewModel.createInventoryItem(sku, name, itemType, unit, warehouse, method, openQty, openCost, marketPrice)
                        sku = ""; name = ""
                    },
                    testTag = "create_inv_item_button"
                )
            }
        }

        if (state.inventoryItems.isNotEmpty()) {
            item {
                FvgCard {
                    Text(if (isArabic) "تسجيل حركة مخزنية وترحيل قيد تلقائي" else "Record Stock Movement (Receipt / Issue / Transfer)", style = MaterialTheme.typography.titleMedium)
                    FvgChipSelector(
                        options = state.inventoryItems.map { it.id },
                        selected = selectedItem?.id ?: "",
                        onSelect = { id -> selectedItem = state.inventoryItems.firstOrNull { it.id == id } },
                        labelResolver = { id -> state.inventoryItems.firstOrNull { it.id == id }?.name ?: id }
                    )
                    FvgChipSelector(
                        options = listOf("receipt", "issue", "transfer", "return", "adjustment"),
                        selected = moveType,
                        onSelect = { moveType = it }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgField(moveQty, { moveQty = it }, "Quantity", numeric = true, modifier = Modifier.weight(1f))
                        FvgField(moveCost, { moveCost = it }, "Unit Cost (if receipt)", numeric = true, modifier = Modifier.weight(1f))
                    }
                    FvgField(moveReason, { moveReason = it }, "Reason / Reference")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = postAutoJe, onCheckedChange = { postAutoJe = it })
                        Text(if (isArabic) "ترحيل قيد محاسبي مزدوج تلقائياً" else "Auto-post double-entry accounting journal", style = MaterialTheme.typography.bodyMedium)
                    }
                    FvgGoldButton(
                        text = if (isArabic) "تأكيد الحركة المخزنية" else "Post Inventory Movement",
                        enabled = state.canEditFinancialData && selectedItem != null && moveQty.isNotBlank() && !state.isBusy,
                        onClick = {
                            selectedItem?.let { item ->
                                viewModel.recordInventoryMovement(
                                    item, moveType, moveQty, moveCost.ifBlank { item.movingAvgCost },
                                    item.warehouse, moveReason, "MOV-$today", today, postAutoJe
                                )
                                moveQty = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        items(state.inventoryItems, key = { it.id }) { item ->
            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${item.sku} • ${item.name}", style = MaterialTheme.typography.titleMedium)
                    FvgRoleBadge(item.valuationMethod.uppercase())
                }
                Text("Qty on Hand: ${item.quantityOnHand} ${item.unit} (${item.warehouse})", style = MaterialTheme.typography.bodyMedium)
                Text("Book Value: ${item.totalBookValue} ${item.currency} | Avg Cost: ${item.movingAvgCost} | Market Price: ${item.currentMarketPrice}", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (state.inventoryMovements.isNotEmpty()) {
            item {
                Text(
                    text = if (isArabic) "سجل الحركات المخزنية (${state.inventoryMovements.size})" else "Stock Movement History Ledger (${state.inventoryMovements.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(state.inventoryMovements.reversed().take(20), key = { it.id }) { mv ->
                FvgCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${mv.itemName} (${mv.movementDate})", style = MaterialTheme.typography.titleMedium)
                        FvgRoleBadge(mv.movementType)
                    }
                    Text("Qty: ${mv.quantity} @ ${mv.unitCost} = Total ${mv.totalCost} $defaultCurrency • ${mv.warehouse}", style = MaterialTheme.typography.bodyMedium)
                    Text("${mv.referenceNo} — ${mv.reason}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun StocktakingTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    today: String
) {
    var sessionName by remember { mutableStateOf("Monthly Physical Count") }
    var warehouse by remember { mutableStateOf("Main Warehouse") }
    val countInputs = remember { mutableStateMapOf<String, String>() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            if (state.inventoryItems.isEmpty()) {
                FvgEmptyStateCard(fvgStrings(isArabic).emptyNoRecords)
            } else {
                FvgCard {
                    Text(if (isArabic) "بدء جلسة جرد فعلي ومقارنتها بالنظام" else "Physical Stocktake Session (Draft -> Review -> Post)", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgField(sessionName, { sessionName = it }, "Session Name", modifier = Modifier.weight(1.3f))
                        FvgField(warehouse, { warehouse = it }, "Warehouse", modifier = Modifier.weight(1f))
                    }
                    state.inventoryItems.take(8).forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1.2f)) {
                                Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("System: ${item.quantityOnHand} ${item.unit}", style = MaterialTheme.typography.labelSmall)
                            }
                            FvgField(
                                value = countInputs[item.id] ?: item.quantityOnHand,
                                onValueChange = { countInputs[item.id] = it },
                                label = "Physical",
                                numeric = true,
                                modifier = Modifier.weight(0.8f)
                            )
                        }
                    }
                    FvgPrimaryButton(
                        text = if (isArabic) "إرسال الجرد للمراجعة" else "Submit Stocktake for Review",
                        enabled = state.canEditFinancialData && sessionName.isNotBlank() && !state.isBusy,
                        onClick = {
                            val counts = state.inventoryItems.associate { it.id to (countInputs[it.id] ?: it.quantityOnHand) }
                            viewModel.createStocktake(sessionName, warehouse, today, state.inventoryItems, counts)
                        }
                    )
                }
            }
        }

        items(state.stocktakes.reversed(), key = { it.id }) { stk ->
            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${stk.sessionName} (${stk.sessionDate})", style = MaterialTheme.typography.titleMedium)
                    FvgRoleBadge(stk.status)
                }
                Text("Warehouse: ${stk.warehouse} • Discrepancy Value: ${stk.totalDiscrepancyValue}", style = MaterialTheme.typography.bodyMedium)
                if (stk.status != "posted" && state.canAdminOrg) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgGoldButton(
                            text = if (isArabic) "ترحيل التسوية والقيد" else "Approve & Post Adjustment",
                            onClick = { viewModel.processStocktakeDecision(stk, "posted") },
                            modifier = Modifier.weight(1f)
                        )
                        if (stk.status == "in_review") {
                            OutlinedButton(
                                onClick = { viewModel.processStocktakeDecision(stk, "rejected") },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(if (isArabic) "رفض" else "Reject")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScrapLossesTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    today: String,
    defaultCurrency: String
) {
    var selectedItem by remember(state.inventoryItems) { mutableStateOf<InventoryItemRecord?>(state.inventoryItems.firstOrNull()) }
    var lossType by remember { mutableStateOf("scrap") }
    var qtyStr by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var lossDate by remember { mutableStateOf(today) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            if (state.inventoryItems.isEmpty()) {
                FvgEmptyStateCard(if (isArabic) "سجل صنفاً مخزنياً أولاً لرفع طلبات الهدر والتوالف." else "Register an inventory item first before logging scrap or damage requests.")
            } else {
                FvgCard {
                    Text(if (isArabic) "طلب تسجيل هدر / تالف / منتهي الصلاحية" else "Submit Scrap, Damaged or Expired Loss (Requires Admin Approval)", style = MaterialTheme.typography.titleMedium)
                    FvgChipSelector(
                        options = state.inventoryItems.map { it.id },
                        selected = selectedItem?.id ?: "",
                        onSelect = { id -> selectedItem = state.inventoryItems.firstOrNull { it.id == id } },
                        labelResolver = { id -> state.inventoryItems.firstOrNull { it.id == id }?.name ?: id }
                    )
                    FvgChipSelector(listOf("scrap", "damaged", "expired"), lossType, { lossType = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgField(qtyStr, { qtyStr = it }, "Loss Quantity", numeric = true, modifier = Modifier.weight(1f), testTag = "scrap_qty_input")
                        FvgField(lossDate, { lossDate = it }, "Loss Date", modifier = Modifier.weight(1f))
                    }
                    FvgField(reason, { reason = it }, "Loss Cause / Root Reason", testTag = "scrap_reason_input")
                    FvgPrimaryButton(
                        text = if (isArabic) "إرسال للاعتماد الإداري" else "Submit Loss Request for Approval",
                        icon = Icons.Filled.Warning,
                        enabled = state.canEditFinancialData && selectedItem != null && qtyStr.isNotBlank() && reason.isNotBlank() && !state.isBusy,
                        onClick = {
                            selectedItem?.let { item ->
                                viewModel.submitScrapLoss(item, lossType, qtyStr, reason, lossDate)
                                qtyStr = ""; reason = ""
                            }
                        },
                        testTag = "submit_scrap_button"
                    )
                }
            }
        }

        items(state.scrapLosses.reversed(), key = { it.id }) { scr ->
            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${scr.itemName} • ${scr.lossDate}", style = MaterialTheme.typography.titleMedium)
                    FvgRoleBadge("${scr.lossType} • ${scr.status}")
                }
                Text("Qty: ${scr.quantity} × ${scr.unitCost} = Total Loss ${scr.totalLossCost} $defaultCurrency", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = FvgNegative)
                Text("Reason: ${scr.reason}", style = MaterialTheme.typography.labelSmall)
                if (scr.status == "pending_approval" && state.canAdminOrg) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgGoldButton(
                            text = if (isArabic) "اعتماد وخصم المخزون وترحيل القيد" else "Approve & Post JE",
                            onClick = { viewModel.reviewScrapLoss(scr, "approved") },
                            modifier = Modifier.weight(1.3f),
                            testTag = "approve_scrap_button"
                        )
                        OutlinedButton(
                            onClick = { viewModel.reviewScrapLoss(scr, "rejected") },
                            modifier = Modifier.weight(0.7f).heightIn(min = 48.dp)
                        ) {
                            Text(if (isArabic) "رفض" else "Reject")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetsTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    today: String,
    defaultCurrency: String
) {
    var name by remember { mutableStateOf("") }
    var budgetType by remember { mutableStateOf("operating") }
    var period by remember { mutableStateOf(today.take(7)) }
    var planned by remember { mutableStateOf("") }
    var actual by remember { mutableStateOf("0") }
    var explanation by remember { mutableStateOf("") }
    var approveNow by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FvgCard {
                Text(if (isArabic) "إنشاء موازنة ومقارنتها بالإنفاق الفعلي" else "Create Budget & Track Planned vs Actual Variance", style = MaterialTheme.typography.titleMedium)
                FvgChipSelector(listOf("operating", "annual", "monthly", "revenue", "expense", "cash"), budgetType, { budgetType = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(name, { name = it }, "Budget Name", modifier = Modifier.weight(1.4f))
                    FvgField(period, { period = it }, "Period", modifier = Modifier.weight(0.8f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(planned, { planned = it }, "Planned ($defaultCurrency)", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(actual, { actual = it }, "Actual ($defaultCurrency)", numeric = true, modifier = Modifier.weight(1f))
                }
                FvgField(explanation, { explanation = it }, "Variance Explanation / Notes")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = approveNow, onCheckedChange = { approveNow = it })
                    Text(if (isArabic) "اعتماد الموازنة فوراً" else "Approve budget immediately", style = MaterialTheme.typography.bodyMedium)
                }
                FvgPrimaryButton(
                    text = if (isArabic) "حفظ الموازنة" else "Save Budget",
                    enabled = state.canEditFinancialData && name.isNotBlank() && planned.isNotBlank() && !state.isBusy,
                    onClick = {
                        viewModel.saveBudget(name, budgetType, period, defaultCurrency, planned, actual, explanation, approveNow)
                        name = ""; planned = ""
                    }
                )
            }
        }

        items(state.budgets, key = { it.id }) { bdg ->
            var newActual by remember(bdg.id, bdg.actualAmount) { mutableStateOf(bdg.actualAmount) }
            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${bdg.name} (${bdg.period})", style = MaterialTheme.typography.titleMedium)
                    FvgRoleBadge("${bdg.budgetType} • ${bdg.status}")
                }
                Text("Planned: ${bdg.plannedAmount} | Actual: ${bdg.actualAmount} | Variance: ${bdg.variance} (${bdg.variancePct}%) ${bdg.currency}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (bdg.explanation.isNotBlank()) {
                    Text(bdg.explanation, style = MaterialTheme.typography.labelSmall)
                }
                if (state.canEditFinancialData) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FvgField(newActual, { newActual = it }, "Update Actual", numeric = true, modifier = Modifier.weight(1f))
                        OutlinedButton(
                            onClick = { viewModel.updateBudgetActuals(bdg, newActual, bdg.explanation, "approved") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(if (isArabic) "تحديث" else "Update")
                        }
                    }
                }
            }
        }
    }
}
