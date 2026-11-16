package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
fun AccountingStatementsScreen(
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

    Column(modifier = Modifier.fillMaxSize().testTag("accounting_statements_screen")) {
        FvgTopHeaderBanner(
            title = if (isArabic) "المحاسبة والأستاذ والقوائم والنسب" else "Accounting, GL & Statements",
            subtitle = s.brandTaglineEn,
            icon = Icons.Filled.AccountBalance,
            isArabic = isArabic,
            isDark = isDark,
            onToggleLanguage = onToggleLanguage,
            onToggleTheme = onToggleTheme,
            orgName = state.activeOrg?.name,
            roleBadge = state.currentUserRole
        )
        FvgSubTabRow(
            tabs = listOf(s.tabJournalCoa, s.tabGeneralLedger, s.tabStatements, s.tabArApAging, s.tabFinancialRatios),
            selectedIndex = subTab,
            onSelect = { subTab = it },
            testTagPrefix = "accounting"
        )

        when (subTab) {
            0 -> JournalAndCoaTab(state, viewModel, isArabic, today, defaultCurrency)
            1 -> GeneralLedgerTab(state, isArabic, defaultCurrency)
            2 -> StatementsAndTrialBalanceTab(state, isArabic, today.take(7), defaultCurrency)
            3 -> ArApAgingTab(state, viewModel, isArabic, today, defaultCurrency)
            4 -> FinancialRatiosTab(state, isArabic)
        }
    }
}

@Composable
private fun JournalAndCoaTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    today: String,
    defaultCurrency: String
) {
    val context = LocalContext.current
    var refNo by remember { mutableStateOf("JE-${(state.journalEntries.size + 1).toString().padStart(3, '0')}") }
    var entryDate by remember { mutableStateOf(today) }
    var description by remember { mutableStateOf("") }
    var debitCode by remember { mutableStateOf("1010") }
    var creditCode by remember { mutableStateOf("4010") }
    var amountStr by remember { mutableStateOf("") }
    var fxRateStr by remember { mutableStateOf("1.0000") }

    var newAccCode by remember { mutableStateOf("") }
    var newAccName by remember { mutableStateOf("") }
    var newAccType by remember { mutableStateOf("expense") }

    val accCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val mime = context.contentResolver.getType(uri).orEmpty()
                val text = if (mime.contains("spreadsheet") || uri.toString().endsWith(".xlsx", true)) {
                    context.contentResolver.openInputStream(uri)?.use { FvgExportService.extractXlsxToCsvLines(it) }.orEmpty()
                } else {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                }
                viewModel.previewAccountingCsvImport(text)
            }
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
                    text = if (isArabic) "إنشاء / ترحيل قيد يومية مزدوج متوازن" else "Create / Post Balanced Double-Entry Journal",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(refNo, { refNo = it }, "Reference No", modifier = Modifier.weight(1f))
                    FvgField(entryDate, { entryDate = it }, "Date", modifier = Modifier.weight(1f))
                }
                FvgField(description, { description = it }, if (isArabic) "الوصف" else "Journal Description", testTag = "je_desc_input")

                Text(if (isArabic) "حساب المدين (Debit)" else "Debit Account", style = MaterialTheme.typography.labelLarge)
                if (state.accounts.isNotEmpty()) {
                    FvgChipSelector(
                        options = state.accounts.map { it.code },
                        selected = debitCode,
                        onSelect = { debitCode = it },
                        labelResolver = { c ->
                            val acc = state.accounts.firstOrNull { it.code == c }
                            if (acc != null) "${acc.code} ${if (isArabic && acc.nameAr.isNotBlank()) acc.nameAr else acc.name}" else c
                        }
                    )
                }
                Text(if (isArabic) "حساب الدائن (Credit)" else "Credit Account", style = MaterialTheme.typography.labelLarge)
                if (state.accounts.isNotEmpty()) {
                    FvgChipSelector(
                        options = state.accounts.map { it.code },
                        selected = creditCode,
                        onSelect = { creditCode = it },
                        labelResolver = { c ->
                            val acc = state.accounts.firstOrNull { it.code == c }
                            if (acc != null) "${acc.code} ${if (isArabic && acc.nameAr.isNotBlank()) acc.nameAr else acc.name}" else c
                        }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(amountStr, { amountStr = it }, "Amount ($defaultCurrency)", numeric = true, modifier = Modifier.weight(1f), testTag = "je_amount_input")
                    FvgField(fxRateStr, { fxRateStr = it }, "FX Rate", numeric = true, modifier = Modifier.weight(0.7f))
                }

                fun submitJe(status: String) {
                    val amt = FvgFinancialEngine.money(FvgFinancialEngine.d(amountStr))
                    val drName = state.accounts.firstOrNull { it.code == debitCode }?.name ?: debitCode
                    val crName = state.accounts.firstOrNull { it.code == creditCode }?.name ?: creditCode
                    viewModel.postJournalEntry(
                        referenceNo = refNo,
                        entryDate = entryDate,
                        description = description,
                        currency = defaultCurrency,
                        exchangeRate = fxRateStr,
                        originalCurrency = defaultCurrency,
                        originalAmount = amt.toPlainString(),
                        lines = listOf(
                            FvgFinancialEngine.JournalLineInput(debitCode, drName, amt, BigDecimal.ZERO),
                            FvgFinancialEngine.JournalLineInput(creditCode, crName, BigDecimal.ZERO, amt)
                        ),
                        status = status
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = state.canEditFinancialData && description.isNotBlank() && amountStr.isNotBlank() && debitCode != creditCode && !state.isBusy,
                        onClick = { submitJe("draft") },
                        modifier = Modifier.weight(0.9f).heightIn(min = 48.dp)
                    ) {
                        Text(if (isArabic) "حفظ كمسودة" else "Save Draft")
                    }
                    FvgPrimaryButton(
                        text = if (isArabic) "ترحيل القيد" else "Post Entry",
                        icon = Icons.Filled.CheckCircle,
                        enabled = state.canEditFinancialData && description.isNotBlank() && amountStr.isNotBlank() && debitCode != creditCode && !state.isBusy,
                        onClick = { submitJe("posted") },
                        modifier = Modifier.weight(1.2f),
                        testTag = "post_journal_button"
                    )
                }
            }
        }

        // Atomic Accounting CSV Import Card
        item {
            FvgCard {
                Text(
                    text = if (isArabic) "استيراد القيود المحاسبية CSV / XLSX بأمان" else "Sanitized Accounting CSV / XLSX Import",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { accCsvLauncher.launch(arrayOf("text/csv", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "*/*")) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text(if (isArabic) "اختيار ملف قيود" else "Pick Journal File")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.previewAccountingCsvImport(
                                "date,refNo,description,debitCode,creditCode,amount\n$today,JE-IMP-1,Cash Consulting Revenue,1010,4010,1200.00\n$today,JE-IMP-2,Office Rent Expense,6010,1010,350.00"
                            )
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text(if (isArabic) "معاينة نموذج قيود" else "Preview Sample CSV")
                    }
                }
                if (state.accountingCsvPreviewRows.isNotEmpty()) {
                    state.accountingCsvPreviewRows.take(6).forEach { r ->
                        val ok = r.error == null
                        Text(
                            text = if (ok) "✓ Row ${r.rowNumber}: ${r.referenceNo} Dr ${r.debitAccountCode} / Cr ${r.creditAccountCode} = ${r.amount}" else "✗ ${r.error}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ok) FvgPositive else FvgNegative
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgGoldButton(
                            text = if (isArabic) "اعتماد وترحيل القيود" else "Commit Accounting Entries",
                            enabled = state.canEditFinancialData && state.accountingCsvPreviewRows.any { it.error == null },
                            onClick = { viewModel.commitAccountingCsvImport() },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = { viewModel.clearAccountingCsvPreview() }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(if (isArabic) "مسح" else "Clear")
                        }
                    }
                }
            }
        }

        // Add custom account
        item {
            FvgCard {
                Text(
                    text = if (isArabic) "إضافة حساب إلى دليل الحسابات (${state.accounts.size})" else "Chart of Accounts (${state.accounts.size} accounts)",
                    style = MaterialTheme.typography.titleMedium
                )
                FvgChipSelector(
                    options = listOf("asset", "liability", "equity", "revenue", "cogs", "expense"),
                    selected = newAccType,
                    onSelect = { newAccType = it }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(newAccCode, { newAccCode = it }, "Code (e.g. 6310)", modifier = Modifier.weight(0.8f))
                    FvgField(newAccName, { newAccName = it }, "Account Name", modifier = Modifier.weight(1.4f))
                }
                FvgGoldButton(
                    text = if (isArabic) "إضافة حساب جديد" else "Add Account",
                    enabled = state.canEditFinancialData && newAccCode.isNotBlank() && newAccName.isNotBlank() && !state.isBusy,
                    onClick = {
                        val sub = when (newAccType) {
                            "asset" -> "current_asset"
                            "liability" -> "current_liability"
                            "equity" -> "equity"
                            "revenue" -> "revenue"
                            "cogs" -> "cogs"
                            else -> "operating_expense"
                        }
                        val norm = if (newAccType in setOf("asset", "cogs", "expense")) "debit" else "credit"
                        viewModel.createAccount(newAccCode, newAccName, "", newAccType, sub, norm, "0.00")
                        newAccCode = ""; newAccName = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        items(state.journalEntries.reversed(), key = { it.id }) { je ->
            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${je.referenceNo} • ${je.entryDate}", style = MaterialTheme.typography.titleMedium)
                    FvgRoleBadge(je.status)
                }
                Text(je.description, style = MaterialTheme.typography.bodyMedium)
                Text("Debits: ${je.totalDebit} = Credits: ${je.totalCredit} ${je.currency}", style = MaterialTheme.typography.labelLarge, color = FvgForestGreen)
                if (je.status == "draft" && state.canEditFinancialData) {
                    FvgGoldButton(
                        text = if (isArabic) "اعتماد وترحيل المسودة" else "Approve & Post Draft",
                        onClick = { viewModel.approveAndPostDraft(je) },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (je.status == "posted" && state.canEditFinancialData) {
                    OutlinedButton(
                        onClick = { viewModel.reverseJournalEntry(je, today) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(if (isArabic) "عكس القيد (Reversal)" else "Create Reversal Entry")
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneralLedgerTab(
    state: FvgUiState,
    isArabic: Boolean,
    currency: String
) {
    var selectedCode by remember(state.accounts) { mutableStateOf(state.accounts.firstOrNull()?.code ?: "1010") }
    var periodFilter by remember { mutableStateOf("") }

    val selectedAcc = state.accounts.firstOrNull { it.code == selectedCode }
    val statement = remember(selectedAcc, state.journalEntries, periodFilter) {
        selectedAcc?.let { acc ->
            val accInput = FvgFinancialEngine.AccountInput(
                acc.code, acc.name, acc.accountType, acc.subType, acc.normalBalance,
                FvgFinancialEngine.dOrZero(acc.openingBalance)
            )
            val posted = state.journalEntries.filter { it.status == "posted" || it.status == "reversed" }.map { je ->
                val lines = je.lines.map { l ->
                    FvgFinancialEngine.JournalLineInput(
                        accountCode = l["accountCode"]?.toString().orEmpty(),
                        accountName = l["accountName"]?.toString().orEmpty(),
                        debit = FvgFinancialEngine.dOrZero(l["debit"]?.toString()),
                        credit = FvgFinancialEngine.dOrZero(l["credit"]?.toString())
                    )
                }
                Triple(je.entryDate, je.referenceNo, je.description to lines)
            }
            FvgFinancialEngine.computeAccountLedgerStatement(accInput, posted, periodFilter)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FvgCard {
                Text(if (isArabic) "دفتر الأستاذ العام والرصيد المتحرك" else "General Ledger & Running Balance Statement", style = MaterialTheme.typography.titleMedium)
                if (state.accounts.isNotEmpty()) {
                    FvgChipSelector(
                        options = state.accounts.map { it.code },
                        selected = selectedCode,
                        onSelect = { selectedCode = it },
                        labelResolver = { c ->
                            val a = state.accounts.firstOrNull { it.code == c }
                            if (a != null) "${a.code} ${if (isArabic && a.nameAr.isNotBlank()) a.nameAr else a.name}" else c
                        }
                    )
                }
                FvgField(periodFilter, { periodFilter = it }, if (isArabic) "تصفية الفترة (مثال 2026-11)" else "Period Filter (optional YYYY-MM)")
            }
        }

        if (statement == null) {
            item { FvgEmptyStateCard(fvgStrings(isArabic).emptyNoRecords) }
        } else {
            item {
                FvgCard(containerColor = FvgSoftCream) {
                    Text("${statement.accountCode} — ${statement.accountName}", style = MaterialTheme.typography.titleLarge, color = FvgDeepGreen)
                    StatementRow("Opening Balance", "${statement.openingBalance} $currency")
                    StatementRow("Total Debits", "${statement.totalDebits} $currency")
                    StatementRow("Total Credits", "${statement.totalCredits} $currency")
                    HorizontalDivider()
                    StatementRow("Closing Balance", "${statement.closingBalance} $currency", bold = true)
                }
            }
            if (statement.movements.isEmpty()) {
                item { FvgEmptyStateCard(if (isArabic) "لا توجد حركات مرحّلة لهذا الحساب في الفترة المحددة." else "No posted movements for this account/period.") }
            } else {
                items(statement.movements) { mv ->
                    FvgCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${mv.entryDate} • ${mv.referenceNo}", style = MaterialTheme.typography.labelLarge, color = FvgDeepGreen)
                            Text("Bal: ${mv.runningBalance} $currency", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = FvgForestGreen)
                        }
                        Text(mv.description, style = MaterialTheme.typography.bodyMedium)
                        Text("Dr: ${mv.debit}  |  Cr: ${mv.credit}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatementsAndTrialBalanceTab(
    state: FvgUiState,
    isArabic: Boolean,
    defaultPeriod: String,
    currency: String
) {
    val report = state.statementsReport
    var periodA by remember { mutableStateOf(defaultPeriod) }
    var periodB by remember { mutableStateOf(defaultPeriod) }

    val profitBridge = remember(state.accounts, state.journalEntries, periodA, periodB) {
        if (state.accounts.isEmpty() || periodA.isBlank() || periodB.isBlank()) null
        else {
            val accInputs = state.accounts.map {
                FvgFinancialEngine.AccountInput(it.code, it.name, it.accountType, it.subType, it.normalBalance, FvgFinancialEngine.dOrZero(it.openingBalance))
            }
            val dated = state.journalEntries.filter { it.status == "posted" || it.status == "reversed" }.map { je ->
                val p = je.period.ifBlank { je.entryDate.take(7) }
                val lines = je.lines.map { l ->
                    FvgFinancialEngine.JournalLineInput(
                        l["accountCode"]?.toString().orEmpty(),
                        l["accountName"]?.toString().orEmpty(),
                        FvgFinancialEngine.dOrZero(l["debit"]?.toString()),
                        FvgFinancialEngine.dOrZero(l["credit"]?.toString())
                    )
                }
                p to lines
            }
            FvgFinancialEngine.comparePeriodsProfitBridge(accInputs, dated, periodA, periodB)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (report == null) {
            item { FvgEmptyStateCard(fvgStrings(isArabic).emptyNoRecords) }
        } else {
            item {
                FvgCard(containerColor = FvgSoftCream) {
                    Text(if (isArabic) "قائمة الدخل" else "Income Statement", style = MaterialTheme.typography.titleLarge, color = FvgDeepGreen)
                    StatementRow("Operating Revenue", "${report.revenue} $currency")
                    StatementRow("Cost of Goods Sold", "(${report.cogs}) $currency")
                    HorizontalDivider()
                    StatementRow("Gross Profit", "${report.grossProfit} $currency", bold = true)
                    StatementRow("Operating Expenses", "(${report.operatingExpenses}) $currency")
                    StatementRow("Operating Profit", "${report.operatingProfit} $currency")
                    StatementRow("Interest & Other", "(${report.interestAndOther}) $currency")
                    HorizontalDivider()
                    StatementRow("Net Profit / (Loss)", "${report.netProfit} $currency", bold = true)
                }
            }

            item {
                FvgCard {
                    Text(if (isArabic) "جسر مقارنة الربح بين فترتين" else "Period A vs Period B Net Profit Bridge", style = MaterialTheme.typography.titleMedium, color = FvgDeepGreen)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgField(periodA, { periodA = it }, "Period A (YYYY-MM)", modifier = Modifier.weight(1f))
                        FvgField(periodB, { periodB = it }, "Period B (YYYY-MM)", modifier = Modifier.weight(1f))
                    }
                    if (profitBridge != null) {
                        StatementRow("Revenue Delta", "${profitBridge.revenueDelta} $currency")
                        StatementRow("COGS Delta", "${profitBridge.cogsDelta} $currency")
                        StatementRow("OpEx Delta", "${profitBridge.opExDelta} $currency")
                        StatementRow("Net Profit Delta", "${profitBridge.netProfitDelta} $currency", bold = true)
                        Text(profitBridge.explanation, style = MaterialTheme.typography.labelSmall, color = FvgForestGreen)
                    }
                }
            }

            item {
                FvgCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isArabic) "الميزانية العمومية" else "Balance Sheet", style = MaterialTheme.typography.titleLarge, color = FvgDeepGreen)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (report.isAccountingEquationBalanced) FvgPositive.copy(alpha = 0.14f) else FvgNegative.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = if (report.isAccountingEquationBalanced) "A = L + E ✓" else "Out of Balance!",
                                color = if (report.isAccountingEquationBalanced) FvgPositive else FvgNegative,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    StatementRow("Cash & Bank", "${report.cashAndBank} $currency")
                    StatementRow("Accounts Receivable", "${report.receivables} $currency")
                    StatementRow("Inventory (Book Value)", "${report.inventoryBookValue} $currency")
                    StatementRow("Non-Current Assets", "${report.nonCurrentAssets} $currency")
                    StatementRow("Total Assets", "${report.totalAssets} $currency", bold = true)
                    HorizontalDivider()
                    StatementRow("Total Liabilities", "${report.totalLiabilities} $currency")
                    StatementRow("Total Equity (incl. Net Income)", "${report.totalEquityWithNetIncome} $currency")
                    StatementRow("Total Liabilities & Equity", "${report.totalLiabilitiesAndEquity} $currency", bold = true)
                }
            }

            item {
                FvgCard {
                    Text(if (isArabic) "قائمة التدفقات النقدية" else "Cash Flow Statement", style = MaterialTheme.typography.titleLarge, color = FvgDeepGreen)
                    StatementRow("Operating Cash Flow", "${report.operatingCashFlow} $currency")
                    StatementRow("Investing Cash Flow", "${report.investingCashFlow} $currency")
                    StatementRow("Financing Cash Flow", "${report.financingCashFlow} $currency")
                    HorizontalDivider()
                    StatementRow("Closing Cash Balance", "${report.closingCash} $currency", bold = true)
                }
            }

            item {
                FvgCard {
                    Text(if (isArabic) "ميزان المراجعة (مدين ${report.totalTrialDebits} | دائن ${report.totalTrialCredits})" else "Trial Balance (Dr ${report.totalTrialDebits} = Cr ${report.totalTrialCredits})", style = MaterialTheme.typography.titleMedium)
                    report.trialBalance.forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${row.code} ${row.name}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.4f))
                            Text("${row.debitBalance}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
                            Text("${row.creditBalance}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatementRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold)
    }
}

@Composable
private fun ArApAgingTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    today: String,
    defaultCurrency: String
) {
    var cpType by remember { mutableStateOf("receivable") }
    var partyName by remember { mutableStateOf("") }
    var invoiceNo by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(today) }

    val agingSummary = remember(state.counterpartyItems) {
        FvgFinancialEngine.computeArApAgingBuckets(
            items = state.counterpartyItems.map {
                val rem = FvgFinancialEngine.dOrZero(it.amount).subtract(FvgFinancialEngine.dOrZero(it.paidAmount))
                Triple(it.dueDate, rem, it.status)
            },
            asOfEpochDays = System.currentTimeMillis() / 86400_000L
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FvgCard(containerColor = FvgSoftCream) {
                Text(if (isArabic) "تقرير أعمار الذمم والفواتير (AR/AP Aging)" else "AR / AP Aging Buckets Summary", style = MaterialTheme.typography.titleMedium, color = FvgDeepGreen)
                StatementRow("Current (Not Past Due)", "${agingSummary.currentAmount} $defaultCurrency")
                StatementRow("1–30 Days Past Due", "${agingSummary.days1To30Amount} $defaultCurrency")
                StatementRow("31–60 Days Past Due", "${agingSummary.days31To60Amount} $defaultCurrency")
                StatementRow("61–90 Days Past Due", "${agingSummary.days61To90Amount} $defaultCurrency")
                StatementRow("90+ Days Past Due", "${agingSummary.days90PlusAmount} $defaultCurrency", bold = true)
                Text("Overdue Open Invoices: ${agingSummary.overdueCount}", style = MaterialTheme.typography.labelLarge, color = if (agingSummary.overdueCount > 0) FvgNegative else FvgPositive)
            }
        }

        item {
            FvgCard {
                Text(if (isArabic) "تسجيل فاتورة ذمم مدينة / دائنة" else "Record AR / AP Invoice", style = MaterialTheme.typography.titleMedium)
                FvgChipSelector(listOf("receivable", "payable"), cpType, { cpType = it })
                FvgField(partyName, { partyName = it }, "Customer / Supplier Name")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(invoiceNo, { invoiceNo = it }, "Invoice No", modifier = Modifier.weight(1f))
                    FvgField(amountStr, { amountStr = it }, "Amount", numeric = true, modifier = Modifier.weight(1f))
                }
                FvgField(dueDate, { dueDate = it }, "Due Date (YYYY-MM-DD)")
                FvgPrimaryButton(
                    text = if (isArabic) "حفظ الفاتورة" else "Save AR/AP Item",
                    enabled = state.canEditFinancialData && partyName.isNotBlank() && invoiceNo.isNotBlank() && amountStr.isNotBlank() && !state.isBusy,
                    onClick = {
                        viewModel.saveCounterpartyItem(cpType, partyName, invoiceNo, defaultCurrency, amountStr, dueDate)
                        partyName = ""; invoiceNo = ""; amountStr = ""
                    },
                    testTag = "save_arap_button"
                )
            }
        }

        items(state.counterpartyItems, key = { it.id }) { item ->
            val bal = FvgFinancialEngine.money(
                FvgFinancialEngine.dOrZero(item.amount).subtract(FvgFinancialEngine.dOrZero(item.paidAmount))
            )
            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${item.partyName} (${item.invoiceNo})", style = MaterialTheme.typography.titleMedium)
                    FvgRoleBadge("${item.type} • ${item.status}")
                }
                Text("Total: ${item.amount} | Paid: ${item.paidAmount} | Remaining: $bal ${item.currency} (Due ${item.dueDate})", style = MaterialTheme.typography.bodyMedium)
                if (bal > BigDecimal.ZERO && state.canEditFinancialData) {
                    FvgGoldButton(
                        text = if (isArabic) "تسديد المتبقي بالكامل" else "Record Full Settlement ($bal)",
                        onClick = { viewModel.recordCounterpartyPayment(item, bal.toPlainString()) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun FinancialRatiosTab(
    state: FvgUiState,
    isArabic: Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (state.financialRatios.isEmpty()) {
            item { FvgEmptyStateCard(fvgStrings(isArabic).emptyNoRecords) }
        } else {
            items(state.financialRatios, key = { it.key }) { ratio ->
                FvgCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ratio.key.replace("_", " ").uppercase(), style = MaterialTheme.typography.titleMedium, color = FvgDeepGreen)
                            Text(ratio.category, style = MaterialTheme.typography.labelSmall, color = FvgAntiqueGold)
                        }
                        Text(
                            text = ratio.value?.let { "${it.toPlainString()} ${ratio.unitSuffix}" } ?: "N/A",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (ratio.value == null) FvgWarning else FvgForestGreen
                        )
                    }
                    Text("Formula: ${ratio.formula}  |  Inputs: ${ratio.inputsSummary}", style = MaterialTheme.typography.labelSmall)
                    Text(ratio.explanation, style = MaterialTheme.typography.bodyMedium)
                    if (ratio.missingDataWarning != null) {
                        Surface(shape = RoundedCornerShape(10.dp), color = FvgSoftCream, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "⚠ ${ratio.missingDataWarning}",
                                style = MaterialTheme.typography.labelSmall,
                                color = FvgNegative,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
