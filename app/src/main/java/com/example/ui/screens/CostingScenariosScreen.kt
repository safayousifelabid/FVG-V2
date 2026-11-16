package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CostSnapshot
import com.example.data.model.ProductionOrderRecord
import com.example.data.model.StandardCostSheet
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
fun CostingScenariosScreen(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    isDark: Boolean,
    onToggleLanguage: () -> Unit,
    onToggleTheme: () -> Unit
) {
    val s = fvgStrings(isArabic)
    var subTab by remember { mutableIntStateOf(0) }
    val defaultCurrency = state.activeOrg?.baseCurrency ?: "SDG"
    val currentPeriod = remember { SimpleDateFormat("yyyy-MM", Locale.US).format(Date()) }

    Column(modifier = Modifier.fillMaxSize().testTag("costing_scenarios_screen")) {
        FvgTopHeaderBanner(
            title = if (isArabic) "التكاليف والمخاطر والانحراف" else "Costing, Risk & Scenarios",
            subtitle = s.brandTaglineEn,
            icon = Icons.Filled.Calculate,
            isArabic = isArabic,
            isDark = isDark,
            onToggleLanguage = onToggleLanguage,
            onToggleTheme = onToggleTheme,
            orgName = state.activeOrg?.name,
            roleBadge = state.currentUserRole
        )
        FvgSubTabRow(
            tabs = listOf(s.tabProductCosting, s.tabCostingCvp, s.tabStandardsProd, s.tabWhatIfScenarios),
            selectedIndex = subTab,
            onSelect = { subTab = it },
            testTagPrefix = "costing"
        )

        when (subTab) {
            0 -> ProductCostingTab(state, viewModel, isArabic, currentPeriod, defaultCurrency)
            1 -> CvpAndRiskTab(state, isArabic, defaultCurrency)
            2 -> StandardsAndVarianceTab(state, viewModel, isArabic, currentPeriod, defaultCurrency)
            3 -> WhatIfScenariosTab(state, viewModel, isArabic)
        }
    }
}

@Composable
private fun ProductCostingTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    defaultPeriod: String,
    defaultCurrency: String
) {
    var selectedProductId by remember { mutableStateOf("") }
    var productName by remember { mutableStateOf("") }
    var period by remember { mutableStateOf(defaultPeriod) }
    var method by remember { mutableStateOf("actual") }
    var directMat by remember { mutableStateOf("0") }
    var indirectMat by remember { mutableStateOf("0") }
    var directLab by remember { mutableStateOf("0") }
    var indirectLab by remember { mutableStateOf("0") }
    var overhead by remember { mutableStateOf("0") }
    var energyFuel by remember { mutableStateOf("0") }
    var packaging by remember { mutableStateOf("0") }
    var transport by remember { mutableStateOf("0") }
    var wasteScrap by remember { mutableStateOf("0") }
    var fixedCosts by remember { mutableStateOf("0") }
    var goodUnits by remember { mutableStateOf("100") }
    var sellingPrice by remember { mutableStateOf("0") }
    var completedEquivUnitsStr by remember { mutableStateOf("100") }

    var targetPct by remember { mutableStateOf("20") }
    var abcPool by remember { mutableStateOf("1000") }
    var abcTotalDriver by remember { mutableStateOf("100") }
    var abcSelectedDriver by remember { mutableStateOf("25") }

    val liveInput = remember(
        directMat, indirectMat, directLab, indirectLab, overhead, energyFuel,
        packaging, transport, wasteScrap, fixedCosts, goodUnits, sellingPrice
    ) {
        runCatching {
            FvgFinancialEngine.CostingInput(
                directMaterial = FvgFinancialEngine.dOrZero(directMat),
                indirectMaterial = FvgFinancialEngine.dOrZero(indirectMat),
                directLabor = FvgFinancialEngine.dOrZero(directLab),
                indirectLabor = FvgFinancialEngine.dOrZero(indirectLab),
                overhead = FvgFinancialEngine.dOrZero(overhead),
                energyFuel = FvgFinancialEngine.dOrZero(energyFuel),
                packaging = FvgFinancialEngine.dOrZero(packaging),
                transport = FvgFinancialEngine.dOrZero(transport),
                wasteScrap = FvgFinancialEngine.dOrZero(wasteScrap),
                fixedCosts = FvgFinancialEngine.dOrZero(fixedCosts),
                goodUnits = FvgFinancialEngine.d(goodUnits),
                sellingPrice = FvgFinancialEngine.dOrZero(sellingPrice)
            )
        }.getOrNull()
    }

    val targetMarginDecimal = remember(targetPct) {
        runCatching { FvgFinancialEngine.d(targetPct) }.getOrDefault(BigDecimal("20"))
    }
    val completedEquivDecimal = remember(completedEquivUnitsStr, method) {
        if (method == "process") runCatching { FvgFinancialEngine.d(completedEquivUnitsStr) }.getOrNull() else null
    }

    val methodOutcome = remember(liveInput, method, targetMarginDecimal, completedEquivDecimal) {
        liveInput?.let {
            runCatching {
                FvgFinancialEngine.calculateCostingByMethod(
                    method = method,
                    input = it,
                    targetMarginPct = targetMarginDecimal,
                    completedEquivUnits = completedEquivDecimal
                )
            }.getOrNull()
        }
    }

    val latestInfPct = state.inflationRecords.firstOrNull()?.monthlyRatePct?.let {
        runCatching { FvgFinancialEngine.d(it) }.getOrNull()
    }
    val liveRisk = remember(methodOutcome, latestInfPct, state.statementsReport) {
        methodOutcome?.coreResult?.let { res ->
            FvgFinancialEngine.evaluateFinancialRisk(
                profitMarginPct = res.profitMarginPct,
                inflationRatePct = latestInfPct,
                totalDebt = state.statementsReport?.totalLiabilities ?: BigDecimal.ZERO,
                totalEquity = state.statementsReport?.totalEquityWithNetIncome ?: BigDecimal.ZERO
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FvgCard {
                Text(
                    text = if (isArabic) "حاسبة التكلفة والأساليب الـ 8 والمخاطر" else "8-Method Product Costing & Pricing Calculator",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (state.products.isNotEmpty()) {
                    FvgChipSelector(
                        options = state.products.map { it.id },
                        selected = selectedProductId,
                        onSelect = { id ->
                            selectedProductId = id
                            state.products.firstOrNull { it.id == id }?.let { productName = it.name }
                        },
                        labelResolver = { id -> state.products.firstOrNull { it.id == id }?.name ?: id }
                    )
                }
                FvgField(productName, { productName = it }, if (isArabic) "اسم المنتج" else "Product Name", testTag = "costing_product_name_input")
                Text(
                    text = if (isArabic) "أسلوب التكلفة (8 أساليب محاسبية):" else "Costing Method (8 Management Accounting Methods):",
                    style = MaterialTheme.typography.labelLarge
                )
                Box(modifier = Modifier.testTag("costing_method_selector")) {
                    FvgChipSelector(
                        options = FvgFinancialEngine.ALL_COSTING_METHODS,
                        selected = method,
                        onSelect = { method = it },
                        labelResolver = { m ->
                            when (m) {
                                "actual" -> if (isArabic) "فعلي (Actual)" else "Actual"
                                "standard" -> if (isArabic) "معياري (Standard)" else "Standard"
                                "target" -> if (isArabic) "مستهدف (Target)" else "Target"
                                "abc" -> "ABC"
                                "job_order" -> if (isArabic) "أوامر (Job Order)" else "Job Order"
                                "process" -> if (isArabic) "مراحل (Process)" else "Process"
                                "absorption" -> if (isArabic) "كلي (Absorption)" else "Absorption"
                                "variable" -> if (isArabic) "متغير (Variable)" else "Variable"
                                else -> m.uppercase()
                            }
                        }
                    )
                }

                if (method == "process") {
                    FvgField(
                        value = completedEquivUnitsStr,
                        onValueChange = { completedEquivUnitsStr = it },
                        label = if (isArabic) "الوحدات المكافئة المكتملة (Process)" else "Completed Equivalent Units (Process)",
                        numeric = true,
                        testTag = "costing_equiv_units_input"
                    )
                }
                if (method == "target") {
                    FvgField(
                        value = targetPct,
                        onValueChange = { targetPct = it },
                        label = if (isArabic) "هامش الربح المستهدف % (Target)" else "Target Profit Margin % (Target Costing)",
                        numeric = true,
                        testTag = "costing_target_margin_input"
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(directMat, { directMat = it }, "Direct Material", numeric = true, modifier = Modifier.weight(1f), testTag = "costing_direct_mat_input")
                    FvgField(indirectMat, { indirectMat = it }, "Indirect Material", numeric = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(directLab, { directLab = it }, "Direct Labor", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(indirectLab, { indirectLab = it }, "Indirect Labor", numeric = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(overhead, { overhead = it }, "Overhead", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(energyFuel, { energyFuel = it }, "Energy & Fuel", numeric = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(packaging, { packaging = it }, "Packaging", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(transport, { transport = it }, "Transport", numeric = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(wasteScrap, { wasteScrap = it }, "Waste / Scrap", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(fixedCosts, { fixedCosts = it }, "Fixed Costs", numeric = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(goodUnits, { goodUnits = it }, "Good Units", numeric = true, modifier = Modifier.weight(1f), testTag = "costing_units_input")
                    FvgField(sellingPrice, { sellingPrice = it }, "Selling Price / Unit", numeric = true, modifier = Modifier.weight(1f), testTag = "costing_price_input")
                }

                if (methodOutcome != null) {
                    val liveResult = methodOutcome.coreResult
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = FvgSoftCream,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(methodOutcome.methodNote, style = MaterialTheme.typography.labelSmall, color = FvgMutedForest)
                            HorizontalDivider(color = FvgWarmGold.copy(alpha = 0.4f))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (isArabic) "التكلفة الإجمالية" else "Total Cost", fontWeight = FontWeight.Bold, color = FvgDeepGreen)
                                Text("${liveResult.totalCost} $defaultCurrency", fontWeight = FontWeight.Bold, color = FvgDeepGreen)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (isArabic) "تكلفة الوحدة الواحدة" else "Unit Cost (${methodOutcome.effectiveUnitsUsed} units)", color = FvgInkDark)
                                Text("${liveResult.unitCost} $defaultCurrency", fontWeight = FontWeight.SemiBold, color = FvgForestGreen)
                            }
                            if (methodOutcome.targetAllowableUnitCost != null && methodOutcome.targetCostGapPerUnit != null) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(if (isArabic) "التكلفة المستهدفة المسموحة" else "Target Allowable Cost", color = FvgInkDark)
                                    Text("${methodOutcome.targetAllowableUnitCost} $defaultCurrency", fontWeight = FontWeight.SemiBold)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(if (isArabic) "فجوة التكلفة المستهدفة" else "Target Cost Gap / Unit", color = FvgInkDark)
                                    Text(
                                        "${methodOutcome.targetCostGapPerUnit} $defaultCurrency",
                                        fontWeight = FontWeight.Bold,
                                        color = if (methodOutcome.targetCostGapPerUnit > BigDecimal.ZERO) FvgNegative else FvgPositive
                                    )
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (isArabic) "هامش الربح (Margin %)" else "Profit Margin % (of Price)", color = FvgInkDark)
                                Text("${liveResult.profitMarginPct ?: "N/A"}%", fontWeight = FontWeight.SemiBold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (isArabic) "نسبة الإضافة (Markup %)" else "Markup % (of Cost)", color = FvgInkDark)
                                Text("${liveResult.markupPct ?: "N/A"}%", fontWeight = FontWeight.SemiBold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (isArabic) "نقطة التعادل (وحدات CEILING)" else "Break-Even (CEILING Units)", color = FvgInkDark)
                                Text("${liveResult.breakEvenUnits ?: "N/A"} units", fontWeight = FontWeight.SemiBold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (isArabic) "صافي الربح" else "Net Profit", fontWeight = FontWeight.Bold, color = FvgDeepGreen)
                                Text(
                                    "${liveResult.netProfit} $defaultCurrency",
                                    fontWeight = FontWeight.Bold,
                                    color = if (liveResult.netProfit.signum() < 0) FvgNegative else FvgPositive
                                )
                            }
                        }
                    }

                    if (liveRisk != null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().testTag("costing_risk_card")
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (isArabic) "محرك تقييم المخاطر المالية" else "Deterministic Risk Engine", style = MaterialTheme.typography.labelLarge, color = FvgDeepGreen)
                                    FvgRiskBadge(level = liveRisk.level, isArabic = isArabic)
                                }
                                Text("${liveRisk.ruleName}: ${liveRisk.actualValue} vs ${liveRisk.thresholdValue}", style = MaterialTheme.typography.labelSmall, color = FvgAntiqueGold)
                                Text(liveRisk.explanation, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                FvgPrimaryButton(
                    text = if (isArabic) "حفظ حساب التكلفة" else "Save Calculation",
                    icon = Icons.Filled.Save,
                    enabled = state.canEditFinancialData && productName.isNotBlank() && liveInput != null && !state.isBusy,
                    onClick = {
                        liveInput?.let {
                            viewModel.saveCosting(
                                productId = selectedProductId,
                                productName = productName,
                                period = period,
                                currency = defaultCurrency,
                                method = method,
                                input = it,
                                completedEquivUnits = completedEquivDecimal,
                                targetMarginPct = targetMarginDecimal
                            )
                        }
                    },
                    testTag = "save_costing_button"
                )
            }
        }

        // Target Pricing & ABC Allocator Helpers
        item {
            FvgCard {
                Text(
                    text = if (isArabic) "التسعير المستهدف وتوزيع التكاليف ABC" else "Target Pricing (Margin vs Markup) & ABC Allocator",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                FvgField(targetPct, { targetPct = it }, "Target Profit %", numeric = true)
                if (methodOutcome != null) {
                    val liveResult = methodOutcome.coreResult
                    val pPct = runCatching { FvgFinancialEngine.d(targetPct) }.getOrNull()
                    if (pPct != null) {
                        val byMargin = runCatching { FvgFinancialEngine.targetPriceByMargin(liveResult.unitCost, pPct).toPlainString() }.getOrElse { "Invalid <100%" }
                        val byMarkup = runCatching { FvgFinancialEngine.targetPriceByMarkup(liveResult.unitCost, pPct).toPlainString() }.getOrElse { "N/A" }
                        Text("At $targetPct% Margin -> Sell at $byMargin | At $targetPct% Markup -> Sell at $byMarkup", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(abcPool, { abcPool = it }, "ABC Pool", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(abcTotalDriver, { abcTotalDriver = it }, "Total Driver", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(abcSelectedDriver, { abcSelectedDriver = it }, "Product Driver", numeric = true, modifier = Modifier.weight(1f))
                }
                val abcShare = runCatching {
                    FvgFinancialEngine.allocateAbcCost(
                        FvgFinancialEngine.d(abcPool),
                        listOf(FvgFinancialEngine.d(abcTotalDriver)),
                        FvgFinancialEngine.d(abcSelectedDriver)
                    )
                }.getOrNull()
                Text("Allocated ABC Overhead: ${abcShare ?: "Invalid driver"} $defaultCurrency", style = MaterialTheme.typography.bodyMedium)
            }
        }

        items(state.costSnapshots, key = { it.id }) { snap ->
            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${snap.productName} (${snap.period})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        FvgRiskBadge(level = snap.riskLevel, isArabic = isArabic)
                        FvgRoleBadge(snap.costingMethod)
                    }
                }
                Text(
                    "Unit Cost: ${snap.unitCost} | Price: ${snap.sellingPrice} | Margin: ${snap.profitMarginPct}% | Markup: ${snap.markupPct}%",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Break-Even: ${snap.breakEvenUnits} units | Total Net Profit: ${snap.netProfit} ${snap.currency}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CvpAndRiskTab(
    state: FvgUiState,
    isArabic: Boolean,
    currency: String
) {
    var mode by remember { mutableStateOf("cvp") }
    var selectedSnap by remember(state.costSnapshots) { mutableStateOf<CostSnapshot?>(state.costSnapshots.firstOrNull()) }

    val volatility = remember(state.costSnapshots) {
        val unitCosts = state.costSnapshots.mapNotNull { runCatching { FvgFinancialEngine.d(it.unitCost) }.getOrNull() }
        FvgFinancialEngine.computeCostVolatility(unitCosts)
    }
    val latestInfPct = state.inflationRecords.firstOrNull()?.monthlyRatePct?.let {
        runCatching { FvgFinancialEngine.d(it) }.getOrNull()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FvgChipSelector(
                options = listOf("cvp", "profitability"),
                selected = mode,
                onSelect = { mode = it },
                labelResolver = {
                    if (it == "cvp") (if (isArabic) "تحليل التعادل CVP" else "CVP Analysis")
                    else (if (isArabic) "الربحية والمخاطر والتقلب" else "Profitability, Risk & Volatility")
                }
            )
        }

        if (state.costSnapshots.isEmpty()) {
            item {
                FvgEmptyStateCard(
                    if (isArabic) "احفظ لقطة تكلفة في التبويب الأول لعرض منحنى التعادل وتقييم المخاطر."
                    else "Save at least one Cost Calculation in 8 Costing Methods to render CVP curve, Margin of Safety, Risk & Volatility."
                )
            }
        } else {
            item {
                FvgCard {
                    Text(if (isArabic) "اختر لقطة التكلفة للتحليل" else "Select Cost Snapshot", style = MaterialTheme.typography.labelLarge)
                    FvgChipSelector(
                        options = state.costSnapshots.map { it.id },
                        selected = selectedSnap?.id ?: "",
                        onSelect = { id -> selectedSnap = state.costSnapshots.firstOrNull { it.id == id } },
                        labelResolver = { id -> state.costSnapshots.firstOrNull { it.id == id }?.let { "${it.productName} (${it.period})" } ?: id }
                    )

                    selectedSnap?.let { snap ->
                        val goodUnits = FvgFinancialEngine.dOrZero(snap.goodUnits).max(BigDecimal.ONE)
                        val fixed = FvgFinancialEngine.dOrZero(snap.fixedCosts)
                        val totCost = FvgFinancialEngine.dOrZero(snap.totalCost)
                        val varTotal = totCost.subtract(fixed).max(BigDecimal.ZERO)
                        val varUnit = varTotal.divide(goodUnits, FvgFinancialEngine.MC)
                        val price = FvgFinancialEngine.dOrZero(snap.sellingPrice)
                        val beUnits = snap.breakEvenUnits.toFloatOrNull()
                        val maxUnits = (beUnits?.let { BigDecimal(it.toDouble() * 1.8) } ?: goodUnits).max(goodUnits)

                        val risk = FvgFinancialEngine.evaluateFinancialRisk(
                            profitMarginPct = runCatching { FvgFinancialEngine.d(snap.profitMarginPct) }.getOrNull(),
                            inflationRatePct = latestInfPct,
                            totalDebt = state.statementsReport?.totalLiabilities ?: BigDecimal.ZERO,
                            totalEquity = state.statementsReport?.totalEquityWithNetIncome ?: BigDecimal.ZERO
                        )

                        if (mode == "cvp") {
                            Surface(shape = RoundedCornerShape(16.dp), color = FvgSoftCream, modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(if (isArabic) "نقطة التعادل" else "Break-Even Point", style = MaterialTheme.typography.labelLarge, color = FvgMutedForest)
                                        Text(
                                            text = "${snap.breakEvenUnits} units",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = FvgDeepGreen
                                        )
                                    }
                                    FvgRiskBadge(level = risk.level, isArabic = isArabic)
                                }
                            }
                            val curve = FvgFinancialEngine.generateCvpCurvePoints(fixed, varUnit, price, maxUnits)
                            FvgCvpChartCanvas(cvpPoints = curve, breakEvenUnits = beUnits)

                            val contribMarginPct = if (price > BigDecimal.ZERO) {
                                FvgFinancialEngine.pct4(price.subtract(varUnit).divide(price, FvgFinancialEngine.MC).multiply(BigDecimal(100))).toPlainString()
                            } else "N/A"
                            val mosPct = if (beUnits != null && goodUnits.toFloat() > 0f) {
                                val mos = ((goodUnits.toFloat() - beUnits) / goodUnits.toFloat()) * 100f
                                String.format("%.2f", mos)
                            } else "N/A"

                            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(if (isArabic) "مؤشرات الأمان والهامش" else "Key CVP Metrics", style = MaterialTheme.typography.titleMedium)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Contribution Margin %")
                                        Text("$contribMarginPct%", fontWeight = FontWeight.Bold, color = FvgForestGreen)
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Margin of Safety %")
                                        Text("$mosPct%", fontWeight = FontWeight.Bold, color = FvgDeepGreen)
                                    }
                                    Text("Risk Rule (${risk.ruleName}): ${risk.explanation}", style = MaterialTheme.typography.labelSmall, color = FvgAntiqueGold)
                                }
                            }
                        } else {
                            val trendPts = state.costSnapshots.takeLast(8).mapNotNull { cs ->
                                cs.unitCost.toFloatOrNull()?.let { cs.period to it }
                            }
                            if (trendPts.size >= 2) {
                                Text(if (isArabic) "تطور تكلفة الوحدة" else "Historical Unit Cost Trend", style = MaterialTheme.typography.titleMedium)
                                FvgSplineLineChartCanvas(points = trendPts)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Gross Profit")
                                Text("${snap.grossProfit} $currency", fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Net Profit")
                                Text("${snap.netProfit} $currency", fontWeight = FontWeight.Bold, color = FvgForestGreen)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Profit Margin % vs Markup %")
                                Text("${snap.profitMarginPct}% / ${snap.markupPct}%", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            if (volatility != null) {
                item {
                    FvgCard(containerColor = FvgSoftCream) {
                        Text(if (isArabic) "تقلب تكلفة الوحدة (Cost Volatility)" else "Unit Cost Volatility Index", style = MaterialTheme.typography.titleMedium, color = FvgDeepGreen)
                        Text(
                            text = "Snapshots: ${volatility.count} | Min: ${volatility.minCost} | Max: ${volatility.maxCost} | Mean: ${volatility.meanCost} $currency",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Spread: ${volatility.spreadPct}% | StdDev: ${volatility.stdDev} $currency",
                            style = MaterialTheme.typography.labelLarge,
                            color = FvgAntiqueGold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StandardsAndVarianceTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    currentPeriod: String,
    defaultCurrency: String
) {
    var stdProductName by remember { mutableStateOf("") }
    var stdMat by remember { mutableStateOf("10.00") }
    var stdHrs by remember { mutableStateOf("1.5") }
    var stdRate by remember { mutableStateOf("6.00") }
    var stdOvh by remember { mutableStateOf("4.00") }
    var stdNotes by remember { mutableStateOf("Baseline standard recipe") }

    var poProductName by remember { mutableStateOf("") }
    var poPeriod by remember { mutableStateOf(currentPeriod) }
    var poPlanned by remember { mutableStateOf("100") }
    var poGood by remember { mutableStateOf("95") }
    var poScrap by remember { mutableStateOf("5") }
    var poActMat by remember { mutableStateOf("1050") }
    var poActLab by remember { mutableStateOf("890") }
    var poActOvh by remember { mutableStateOf("390") }

    // Detailed Variance Decomposition optional inputs
    var stdMatQtyPerUnit by remember { mutableStateOf("2") }
    var stdMatPrice by remember { mutableStateOf("5.00") }
    var actMatQty by remember { mutableStateOf("200") }
    var actMatPrice by remember { mutableStateOf("5.25") }
    var actLaborHrs by remember { mutableStateOf("145") }
    var actLaborRate by remember { mutableStateOf("6.15") }

    var selectedStd by remember(state.standardCosts) { mutableStateOf<StandardCostSheet?>(state.standardCosts.firstOrNull()) }
    var selectedPo by remember(state.productionOrders) { mutableStateOf<ProductionOrderRecord?>(state.productionOrders.firstOrNull()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FvgCard {
                Text(if (isArabic) "التكلفة المعيارية للمنتج (Standard Costing)" else "Standard Cost Sheet (Versioned)", style = MaterialTheme.typography.titleMedium)
                FvgField(stdProductName, { stdProductName = it }, "Product Name", testTag = "std_product_input")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(stdMat, { stdMat = it }, "Std Material/Unit", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(stdOvh, { stdOvh = it }, "Std Overhead/Unit", numeric = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(stdHrs, { stdHrs = it }, "Std Labor Hrs", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(stdRate, { stdRate = it }, "Std Labor Rate", numeric = true, modifier = Modifier.weight(1f))
                }
                FvgField(stdNotes, { stdNotes = it }, "Engineering Notes")
                FvgPrimaryButton(
                    text = if (isArabic) "حفظ بطاقة التكلفة المعيارية" else "Save Standard Cost Sheet",
                    icon = Icons.Filled.Save,
                    enabled = state.canEditFinancialData && stdProductName.isNotBlank() && !state.isBusy,
                    onClick = {
                        viewModel.createStandardCost("", stdProductName, stdMat, stdHrs, stdRate, stdOvh, stdNotes)
                        poProductName = stdProductName
                    },
                    testTag = "save_standard_cost_button"
                )
            }
        }

        item {
            FvgCard {
                Text(if (isArabic) "أمر الإنتاج والمراحل والهدر الفعلي" else "Production Order & Multi-Stage Tracking", style = MaterialTheme.typography.titleMedium)
                FvgField(poProductName, { poProductName = it }, "Product Name", testTag = "po_product_input")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(poPeriod, { poPeriod = it }, "Period", modifier = Modifier.weight(0.9f))
                    FvgField(poPlanned, { poPlanned = it }, "Planned Units", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(poGood, { poGood = it }, "Good Units", numeric = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(poScrap, { poScrap = it }, "Scrap Units", numeric = true, modifier = Modifier.weight(0.9f))
                    FvgField(poActMat, { poActMat = it }, "Actual Material", numeric = true, modifier = Modifier.weight(1.1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(poActLab, { poActLab = it }, "Actual Labor", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(poActOvh, { poActOvh = it }, "Actual Overhead", numeric = true, modifier = Modifier.weight(1f))
                }
                FvgGoldButton(
                    text = if (isArabic) "تسجيل أمر إنتاج" else "Create Production Order",
                    icon = Icons.Filled.Factory,
                    enabled = state.canEditFinancialData && poProductName.isNotBlank() && !state.isBusy,
                    onClick = {
                        viewModel.createProductionOrder("", poProductName, poPeriod, poPlanned, poGood, poScrap, poActMat, poActLab, poActOvh)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "create_po_button"
                )
            }
        }

        if (selectedStd != null && selectedPo != null) {
            item {
                val stdSheet = selectedStd!!
                val po = selectedPo!!
                val detailed = remember(stdSheet, po, stdMatQtyPerUnit, stdMatPrice, actMatQty, actMatPrice, actLaborHrs, actLaborRate) {
                    runCatching {
                        FvgFinancialEngine.computeDetailedVariance(
                            stdMaterialQtyPerUnit = FvgFinancialEngine.dOrZero(stdMatQtyPerUnit),
                            stdMaterialPrice = FvgFinancialEngine.dOrZero(stdMatPrice),
                            stdLaborHoursPerUnit = FvgFinancialEngine.dOrZero(stdSheet.standardLaborHours),
                            stdLaborRate = FvgFinancialEngine.dOrZero(stdSheet.standardLaborRate),
                            stdOverheadPerUnit = FvgFinancialEngine.dOrZero(stdSheet.standardOverheadPerUnit),
                            goodUnits = FvgFinancialEngine.d(po.goodUnits),
                            actualMaterialQty = FvgFinancialEngine.dOrZero(actMatQty),
                            actualMaterialPrice = FvgFinancialEngine.dOrZero(actMatPrice),
                            actualLaborHours = FvgFinancialEngine.dOrZero(actLaborHrs),
                            actualLaborRate = FvgFinancialEngine.dOrZero(actLaborRate),
                            actualOverheadCost = FvgFinancialEngine.dOrZero(po.actualOverheadCost)
                        )
                    }.getOrNull()
                }
                val yieldRes = FvgFinancialEngine.computeScrapYieldAnalysis(
                    plannedUnits = FvgFinancialEngine.d(po.plannedUnits),
                    goodUnits = FvgFinancialEngine.d(po.goodUnits),
                    recordedScrapUnits = FvgFinancialEngine.dOrZero(po.scrapUnits),
                    totalCost = FvgFinancialEngine.dOrZero(po.actualTotalCost)
                )
                FvgCard(containerColor = FvgSoftCream) {
                    Text(
                        text = if (isArabic) "تحليل انحراف السعر والكمية وكفاءة العمل" else "Detailed Material & Labor Variance Decomposition",
                        style = MaterialTheme.typography.titleMedium,
                        color = FvgDeepGreen
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgField(stdMatQtyPerUnit, { stdMatQtyPerUnit = it }, "Std Mat Qty/U", numeric = true, modifier = Modifier.weight(1f))
                        FvgField(stdMatPrice, { stdMatPrice = it }, "Std Mat Price", numeric = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgField(actMatQty, { actMatQty = it }, "Act Mat Qty", numeric = true, modifier = Modifier.weight(1f))
                        FvgField(actMatPrice, { actMatPrice = it }, "Act Mat Price", numeric = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgField(actLaborHrs, { actLaborHrs = it }, "Act Labor Hrs", numeric = true, modifier = Modifier.weight(1f))
                        FvgField(actLaborRate, { actLaborRate = it }, "Act Labor Rate", numeric = true, modifier = Modifier.weight(1f))
                    }
                    if (detailed != null) {
                        val v = detailed.summary
                        Text("Standard Total Allowed: ${v.standardTotalAllowed} $defaultCurrency", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Mat Price Var: ${detailed.materialPriceVariance ?: "N/A"} | Mat Usage Var: ${detailed.materialUsageVariance ?: "N/A"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = FvgForestGreen
                        )
                        Text(
                            "Labor Rate Var: ${detailed.laborRateVariance ?: "N/A"} | Labor Eff Var: ${detailed.laborEfficiencyVariance ?: "N/A"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = FvgForestGreen
                        )
                        Text(
                            text = "Total Cost Variance: ${v.totalVariance ?: "Incomplete"} $defaultCurrency",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if ((v.totalVariance ?: BigDecimal.ZERO) > BigDecimal.ZERO) FvgNegative else FvgPositive
                        )
                        Text(detailed.decompositionNote, style = MaterialTheme.typography.labelSmall, color = FvgMutedForest)
                    }
                    HorizontalDivider()
                    Text("Yield: ${yieldRes.yieldPct}% | Scrap Rate: ${yieldRes.scrapRatePct}% (${yieldRes.recordedScrapUnits} units)", style = MaterialTheme.typography.bodyMedium)
                    Text("Unit Cost (Good): ${yieldRes.currentCostPerGoodUnit} | Indicative After Scrap: ${yieldRes.indicativeCostAfterScrap} $defaultCurrency", style = MaterialTheme.typography.labelLarge, color = FvgForestGreen)
                }
            }
        }

        items(state.productionOrders, key = { it.id }) { po ->
            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${po.productName} (${po.period})", style = MaterialTheme.typography.titleMedium)
                    FvgRoleBadge(po.status)
                }
                Text("Planned: ${po.plannedUnits} | Good: ${po.goodUnits} | Scrap: ${po.scrapUnits} | Actual Total: ${po.actualTotalCost} ${po.currency}", style = MaterialTheme.typography.bodyMedium)
                if (po.status == "in_progress" && state.canEditFinancialData) {
                    OutlinedButton(
                        onClick = {
                            viewModel.completeProductionOrder(po, po.scrapUnits, po.actualMaterialCost, po.actualLaborCost, po.actualOverheadCost)
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isArabic) "إتمام مراحل الإنتاج" else "Complete Stages")
                    }
                }
            }
        }

        items(state.standardCosts, key = { it.id }) { std ->
            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${std.productName} Standard v${std.version}", style = MaterialTheme.typography.titleMedium)
                    FvgRoleBadge("${std.standardUnitCost} ${std.currency}")
                }
                Text("Mat: ${std.standardMaterialCost} | Lab: ${std.standardLaborHours}h × ${std.standardLaborRate} | Ovh: ${std.standardOverheadPerUnit}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun WhatIfScenariosTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean
) {
    val s = fvgStrings(isArabic)
    var selectedSnap by remember(state.costSnapshots) { mutableStateOf<CostSnapshot?>(state.costSnapshots.firstOrNull()) }
    var selectedPresetId by remember { mutableStateOf("raw_mat_15") }
    var scenName by remember { mutableStateOf("Raw Material +15%") }
    var scenType by remember { mutableStateOf("Raw Material +15%") }
    var matPct by remember { mutableStateOf("15") }
    var labPct by remember { mutableStateOf("0") }
    var ovhPct by remember { mutableStateOf("0") }
    var pricePct by remember { mutableStateOf("0") }
    var fxPct by remember { mutableStateOf("0") }
    var volumePct by remember { mutableStateOf("0") }

    val previewScenario = remember(selectedSnap, matPct, labPct, ovhPct, pricePct, fxPct, volumePct) {
        selectedSnap?.let { snap ->
            runCatching {
                val baseMat = FvgFinancialEngine.dOrZero(snap.directMaterial).add(FvgFinancialEngine.dOrZero(snap.indirectMaterial))
                val baseLab = FvgFinancialEngine.dOrZero(snap.directLabor).add(FvgFinancialEngine.dOrZero(snap.indirectLabor))
                val baseOther = FvgFinancialEngine.dOrZero(snap.totalCost).subtract(baseMat).subtract(baseLab).max(BigDecimal.ZERO)
                FvgFinancialEngine.runExtendedScenario(
                    baseMaterial = baseMat,
                    baseLabor = baseLab,
                    baseOverheadAndOther = baseOther,
                    baseRevenue = FvgFinancialEngine.dOrZero(snap.revenue),
                    materialChangePct = FvgFinancialEngine.dOrZero(matPct),
                    laborChangePct = FvgFinancialEngine.dOrZero(labPct),
                    overheadChangePct = FvgFinancialEngine.dOrZero(ovhPct),
                    priceChangePct = FvgFinancialEngine.dOrZero(pricePct),
                    fxChangePct = FvgFinancialEngine.dOrZero(fxPct),
                    volumeChangePct = FvgFinancialEngine.dOrZero(volumePct)
                )
            }.getOrNull()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.scenarios.isNotEmpty() && selectedSnap != null) {
            item {
                val baseP = FvgFinancialEngine.dOrZero(selectedSnap!!.netProfit).toFloat()
                val s2 = state.scenarios.getOrNull(0)?.scenarioProfit?.toFloatOrNull() ?: baseP
                val s3 = state.scenarios.getOrNull(1)?.scenarioProfit?.toFloatOrNull() ?: previewScenario?.scenarioProfit?.toFloat() ?: baseP
                FvgCard(containerColor = FvgSoftCream) {
                    Text(if (isArabic) "مقارنة السيناريو 1 (الحالي) مقابل 2 و 3" else "Scenario 1 (Current) vs Scenario 2 vs Scenario 3", style = MaterialTheme.typography.titleMedium, color = FvgDeepGreen)
                    FvgBarComparisonCanvas(
                        bars = listOf("S1 Current" to baseP, "S2 Saved" to s2, "S3 Alt" to s3)
                    )
                }
            }
        }

        item {
            if (state.costSnapshots.isEmpty()) {
                FvgEmptyStateCard(
                    if (isArabic) "احفظ لقطة تكلفة أولاً في تبويب طرق التكلفة لتشغيل محاكاة ماذا لو."
                    else "Save a Product Cost Snapshot first to simulate non-destructive What-If pricing scenarios."
                )
            } else {
                FvgCard {
                    Text(
                        text = if (isArabic) "محاكاة ماذا لو (التضخم، الصرف، الحجم، التسعير)" else "What-If Scenario Simulator (Spec #45 Presets & Extended Factors)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(shape = RoundedCornerShape(10.dp), color = FvgSoftCream, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = s.scenarioDisclaimer,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = FvgAntiqueGold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    FvgChipSelector(
                        options = state.costSnapshots.map { it.id },
                        selected = selectedSnap?.id ?: "",
                        onSelect = { id -> selectedSnap = state.costSnapshots.firstOrNull { it.id == id } },
                        labelResolver = { id -> state.costSnapshots.firstOrNull { it.id == id }?.productName ?: id }
                    )
                    Text(if (isArabic) "قوالب السيناريوهات القياسية:" else "Quick Scenario Presets:", style = MaterialTheme.typography.labelLarge)
                    FvgChipSelector(
                        options = FvgFinancialEngine.SPEC_SCENARIO_PRESETS.map { it.id },
                        selected = selectedPresetId,
                        onSelect = { id ->
                            selectedPresetId = id
                            FvgFinancialEngine.SPEC_SCENARIO_PRESETS.firstOrNull { it.id == id }?.let { p ->
                                scenType = p.labelEn
                                scenName = if (isArabic) p.labelAr else p.labelEn
                                matPct = p.materialPct
                                labPct = p.laborPct
                                ovhPct = p.overheadPct
                                pricePct = p.pricePct
                                fxPct = p.fxPct
                                volumePct = p.volumePct
                            }
                        },
                        labelResolver = { id ->
                            val p = FvgFinancialEngine.SPEC_SCENARIO_PRESETS.first { it.id == id }
                            if (isArabic) p.labelAr else p.labelEn
                        }
                    )

                    FvgField(scenName, { scenName = it }, "Scenario Name")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgField(matPct, { matPct = it }, "Material Δ %", numeric = true, modifier = Modifier.weight(1f))
                        FvgField(labPct, { labPct = it }, "Labor Δ %", numeric = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgField(ovhPct, { ovhPct = it }, "Overhead Δ %", numeric = true, modifier = Modifier.weight(1f))
                        FvgField(pricePct, { pricePct = it }, "Selling Price Δ %", numeric = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgField(fxPct, { fxPct = it }, "FX Shock Δ %", numeric = true, modifier = Modifier.weight(1f))
                        FvgField(volumePct, { volumePct = it }, "Volume Δ %", numeric = true, modifier = Modifier.weight(1f))
                    }

                    if (previewScenario != null) {
                        FvgBarComparisonCanvas(
                            bars = listOf(
                                "Base Cost" to previewScenario.baseTotalCost.toFloat(),
                                "Scen. Cost" to previewScenario.scenarioTotalCost.toFloat(),
                                "Base Profit" to previewScenario.baseProfit.toFloat(),
                                "Scen. Profit" to previewScenario.scenarioProfit.toFloat()
                            )
                        )
                        Text(
                            text = "Profit Impact: ${previewScenario.profitDelta} (Cost Δ: ${previewScenario.costDelta})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (previewScenario.profitDelta.signum() < 0) FvgNegative else FvgPositive
                        )
                        Text(previewScenario.disclaimer, style = MaterialTheme.typography.labelSmall, color = FvgMutedForest)
                    }
                    FvgGoldButton(
                        text = if (isArabic) "حفظ السيناريو" else "Save What-If Scenario",
                        enabled = state.canEditFinancialData && selectedSnap != null && scenName.isNotBlank() && !state.isBusy,
                        onClick = {
                            selectedSnap?.let {
                                viewModel.saveScenario(it, scenName, matPct, labPct, ovhPct, pricePct, scenType, fxPct, volumePct)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        items(state.scenarios, key = { it.id }) { scn ->
            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(scn.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    FvgRoleBadge(scn.scenarioType)
                }
                Text(
                    "Inputs: Mat ${scn.materialChangePct}% | Lab ${scn.laborChangePct}% | Ovh ${scn.overheadChangePct}% | Price ${scn.priceChangePct}% | FX ${scn.fxChangePct}% | Vol ${scn.volumeChangePct}%",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    "Base Profit: ${scn.baseProfit} -> Scenario Profit: ${scn.scenarioProfit} (${s.scenarioDisclaimer})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FvgDeepGreen
                )
            }
        }
    }
}
