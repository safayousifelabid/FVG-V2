package com.example.ui.screens

import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.export.FvgExportService
import com.example.ui.components.*
import com.example.ui.i18n.fvgStrings
import com.example.ui.theme.*
import com.example.ui.viewmodel.FvgUiState
import com.example.ui.viewmodel.FvgViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReportsAiSettingsScreen(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    isDark: Boolean,
    onToggleLanguage: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenDeveloperIntro: () -> Unit = {},
    onSignOutClick: () -> Unit
) {
    val s = fvgStrings(isArabic)
    var subTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().testTag("reports_ai_settings_screen")) {
        FvgTopHeaderBanner(
            title = if (isArabic) "التقارير والمساعد والإعدادات" else "Reports, AI & Workspace",
            subtitle = s.brandTaglineEn,
            icon = Icons.Filled.Insights,
            isArabic = isArabic,
            isDark = isDark,
            onToggleLanguage = onToggleLanguage,
            onToggleTheme = onToggleTheme,
            orgName = state.activeOrg?.name,
            roleBadge = state.currentUserRole
        )
        FvgSubTabRow(
            tabs = listOf(s.tabReportsExports, s.tabAiOcr, s.tabOrgSettings),
            selectedIndex = subTab,
            onSelect = { subTab = it },
            testTagPrefix = "reports"
        )

        when (subTab) {
            0 -> ReportsExportTab(state, viewModel, isArabic)
            1 -> AiAssistantAndOcrTab(state, viewModel, isArabic)
            2 -> OrgRbacAuditSettingsTab(state, viewModel, isArabic, isDark, onToggleLanguage, onToggleTheme, onOpenDeveloperIntro, onSignOutClick)
        }
    }
}

@Composable
private fun ReportsExportTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean
) {
    val context = LocalContext.current
    val s = fvgStrings(isArabic)
    var selectedReport by remember { mutableStateOf("statements") }
    var exportNotice by remember { mutableStateOf<String?>(null) }

    val payload = remember(selectedReport, state) { viewModel.buildReportPayload(selectedReport) }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FvgExportService.writePdf(out, payload)
                }
                exportNotice = "PDF exported (${payload.rows.size} rows)."
            }.onFailure { exportNotice = "PDF export failed: ${it.message}" }
        }
    }
    val xlsxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FvgExportService.writeXlsx(out, payload)
                }
                exportNotice = "XLSX exported (${payload.rows.size} rows)."
            }.onFailure { exportNotice = "XLSX export failed: ${it.message}" }
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            runCatching {
                FvgExportService.writeTextToUri(context, uri, FvgExportService.toCsvString(payload))
                exportNotice = "CSV exported."
            }.onFailure { exportNotice = "CSV export failed: ${it.message}" }
        }
    }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                FvgExportService.writeTextToUri(context, uri, FvgExportService.toJsonString(payload))
                exportNotice = "JSON exported."
            }.onFailure { exportNotice = "JSON export failed: ${it.message}" }
        }
    }
    val backupJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                FvgExportService.writeTextToUri(context, uri, viewModel.buildWorkspaceBackupJson())
                exportNotice = "Full Organization Workspace Backup (JSON) exported cleanly."
            }.onFailure { exportNotice = "Backup failed: ${it.message}" }
        }
    }

    val stamp = remember { SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) }
    val stmt = state.statementsReport

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (stmt != null) {
            item {
                FvgCard(containerColor = FvgSoftCream) {
                    Text(if (isArabic) "ملخص الأداء السنوي / التراكمي" else "Financial Performance Summary", style = MaterialTheme.typography.titleMedium, color = FvgDeepGreen)
                    FvgBarComparisonCanvas(
                        bars = listOf(
                            "Rev" to stmt.revenue.toFloat(),
                            "COGS" to stmt.cogs.toFloat(),
                            "Gross" to stmt.grossProfit.toFloat(),
                            "OpEx" to stmt.operatingExpenses.toFloat(),
                            "Net" to stmt.netProfit.toFloat()
                        )
                    )
                }
            }
        }

        item {
            FvgCard {
                Text(if (isArabic) "التقارير المالية والتصديرات (12 تقريراً)" else "Financial Reports & Verified Exports (12 Reports)", style = MaterialTheme.typography.titleMedium)
                FvgChipSelector(
                    options = listOf(
                        "statements", "trial_balance", "ledger", "ratios",
                        "prices", "inflation", "fx", "costing",
                        "inventory", "stocktakes", "scrap", "budgets"
                    ),
                    selected = selectedReport,
                    onSelect = { selectedReport = it },
                    labelResolver = {
                        when (it) {
                            "statements" -> if (isArabic) "القوائم" else "Statements"
                            "trial_balance" -> if (isArabic) "ميزان المراجعة" else "Trial Balance"
                            "ledger" -> if (isArabic) "الأستاذ" else "Ledger"
                            "ratios" -> if (isArabic) "النسب" else "Ratios"
                            "prices" -> if (isArabic) "الأسعار" else "Prices"
                            "inflation" -> if (isArabic) "التضخم" else "Inflation"
                            "fx" -> if (isArabic) "الصرف" else "FX Rates"
                            "costing" -> if (isArabic) "التكاليف" else "Costing"
                            "inventory" -> if (isArabic) "المخزون" else "Inventory"
                            "stocktakes" -> if (isArabic) "الجرد" else "Stocktakes"
                            "scrap" -> if (isArabic) "الهدر" else "Scrap"
                            else -> if (isArabic) "الموازنات" else "Budgets"
                        }
                    }
                )
                Text(payload.title, style = MaterialTheme.typography.titleLarge, color = FvgDeepGreen)
                Text("${payload.organizationName} • ${payload.currency} • ${payload.generatedDate}", style = MaterialTheme.typography.labelSmall)
                Text(payload.methodNotes, style = MaterialTheme.typography.bodyMedium, color = FvgMutedForest)

                if (payload.missingDataNotes.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(12.dp), color = FvgSoftCream, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = payload.missingDataNotes.joinToString("\n"),
                            style = MaterialTheme.typography.labelSmall,
                            color = FvgNegative,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (exportNotice != null) {
                    Text(exportNotice!!, style = MaterialTheme.typography.bodyMedium, color = FvgPositive, fontWeight = FontWeight.SemiBold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgPrimaryButton(
                        text = s.exportPdf,
                        icon = Icons.Filled.PictureAsPdf,
                        onClick = { pdfLauncher.launch("fvg_${selectedReport}_$stamp.pdf") },
                        modifier = Modifier.weight(1f),
                        testTag = "export_pdf_button"
                    )
                    FvgGoldButton(
                        text = s.exportXlsx,
                        icon = Icons.Filled.TableChart,
                        onClick = { xlsxLauncher.launch("fvg_${selectedReport}_$stamp.xlsx") },
                        modifier = Modifier.weight(1f),
                        testTag = "export_xlsx_button"
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { csvLauncher.launch("fvg_${selectedReport}_$stamp.csv") },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) { Text(s.exportCsv) }
                    OutlinedButton(
                        onClick = { jsonLauncher.launch("fvg_${selectedReport}_$stamp.json") },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) { Text(s.exportJson) }
                }
                OutlinedButton(
                    onClick = { backupJsonLauncher.launch("fvg_workspace_backup_$stamp.json") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isArabic) "تصدير نسخة احتياطية شاملة JSON" else "Export Full Workspace Backup (JSON)")
                }
            }
        }

        items(payload.rows.take(25)) { row ->
            FvgCard {
                Text(row.joinToString("  |  "), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun AiAssistantAndOcrTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean
) {
    val context = LocalContext.current
    var question by remember { mutableStateOf("") }
    var transcriptNotice by remember { mutableStateOf<String?>(null) }
    val stamp = remember { SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) }
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    val transcriptLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            runCatching {
                val txt = FvgExportService.toChatTranscriptText(
                    organizationName = state.activeOrg?.name ?: "FVG Workspace",
                    aiMode = state.aiMode,
                    generatedDate = today,
                    messages = state.chatTranscript
                )
                FvgExportService.writeTextToUri(context, uri, txt)
                transcriptNotice = if (isArabic) "تم تصدير سجل محادثة الذكاء بنجاح." else "AI transcript exported (.txt)."
            }.onFailure { transcriptNotice = "Export failed: ${it.message}" }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching {
                @Suppress("DEPRECATION")
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                viewModel.extractOcrInvoice(bitmap, uri.lastPathSegment ?: "invoice_doc.jpg")
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
                    text = if (isArabic) "المساعد الذكي متعدد الأوضاع (Firebase AI Logic)" else "3-Mode Grounded AI Assistant (Firebase AI Logic)",
                    style = MaterialTheme.typography.titleMedium,
                    color = FvgDeepGreen
                )
                Text(
                    text = if (isArabic) "اختر وضع المساعد:" else "Select AI Assistant Mode:",
                    style = MaterialTheme.typography.labelLarge
                )
                Box(modifier = Modifier.testTag("ai_mode_selector")) {
                    FvgChipSelector(
                        options = listOf("general", "financial", "research"),
                        selected = state.aiMode,
                        onSelect = { viewModel.setAiMode(it) },
                        labelResolver = { mode ->
                            when (mode) {
                                "general" -> if (isArabic) "إرشاد عام (General)" else "General Guide"
                                "financial" -> if (isArabic) "تحليل مالي (Financial)" else "Financial Analyst"
                                else -> if (isArabic) "بحث أكاديمي (Research)" else "Academic Research"
                            }
                        }
                    )
                }
                Text(
                    text = if (isArabic)
                        "يحلل المساعد سجلات منظمتك الموثقة فقط عبر Firebase AI Logic + App Check دون اختلاق أي أرقام."
                    else
                        "Answers strictly from verified workspace records and thesis structure via Firebase AI Logic + App Check without fabricating metrics.",
                    style = MaterialTheme.typography.bodyMedium
                )
                FvgField(
                    value = question,
                    onValueChange = { question = it },
                    label = if (isArabic) "اسأل عن الأرباح، التضخم، التكلفة الـ 8، المخزون أو البحث" else "Ask about margins, inflation, 8 costing methods, stock, or thesis structure",
                    singleLine = false,
                    testTag = "ai_question_input"
                )
                FvgPrimaryButton(
                    text = if (state.isAiStreaming) (if (isArabic) "جاري التحليل..." else "Analyzing verified records...")
                    else if (isArabic) "تحليل البيانات" else "Analyze System Data",
                    icon = Icons.Filled.AutoAwesome,
                    enabled = !state.isAiStreaming && question.isNotBlank() && state.activeOrg != null,
                    onClick = { viewModel.askAiAssistant(question, if (isArabic) "ar" else "en") },
                    testTag = "ask_ai_button"
                )
                if (state.aiResponse.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(14.dp), color = FvgSoftCream, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = state.aiResponse,
                            style = MaterialTheme.typography.bodyLarge,
                            color = FvgInkDark,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
                if (state.chatTranscript.isNotEmpty()) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isArabic) "سجل الجلسة (${state.chatTranscript.size})" else "Session Transcript (${state.chatTranscript.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = FvgDeepGreen
                        )
                        OutlinedButton(
                            onClick = { transcriptLauncher.launch("fvg_ai_transcript_$stamp.txt") },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("export_transcript_button")
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isArabic) "تصدير TXT" else "Export TXT")
                        }
                    }
                    state.chatTranscript.takeLast(6).forEach { (role, msg) ->
                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(role, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = FvgDeepGreen)
                                Text(msg.take(320), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (transcriptNotice != null) {
                    Text(transcriptNotice!!, style = MaterialTheme.typography.labelLarge, color = FvgPositive)
                }
            }
        }

        item {
            FvgCard {
                Text(
                    text = if (isArabic) "قراءة الفواتير والوثائق (OCR للمراجعة البشرية)" else "Invoice & Receipt OCR (Draft Requires Human Confirmation)",
                    style = MaterialTheme.typography.titleMedium,
                    color = FvgDeepGreen
                )
                FvgGoldButton(
                    text = if (state.isOcrExtracting) (if (isArabic) "جاري استخراج النص..." else "Extracting document fields...")
                    else if (isArabic) "اختيار صورة فاتورة (Photo Picker)" else "Pick Invoice Photo for OCR",
                    icon = Icons.Filled.DocumentScanner,
                    enabled = state.canEditFinancialData && !state.isOcrExtracting && state.activeOrg != null,
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "pick_ocr_photo_button"
                )
            }
        }

        items(state.ocrDrafts.reversed(), key = { it.id }) { draft ->
            var vName by remember(draft.id) { mutableStateOf(draft.vendorName) }
            var dDate by remember(draft.id) { mutableStateOf(draft.documentDate) }
            var dCurr by remember(draft.id) { mutableStateOf(draft.currency) }
            var dAmt by remember(draft.id) { mutableStateOf(draft.totalAmount) }
            var dSum by remember(draft.id) { mutableStateOf(draft.lineSummary) }

            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(draft.fileName, style = MaterialTheme.typography.titleMedium)
                    FvgRoleBadge(draft.reviewStatus)
                }
                if (draft.confidenceNote.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(10.dp), color = FvgSoftCream, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (isArabic) "ملاحظة الموثوقية: ${draft.confidenceNote}" else "OCR Confidence: ${draft.confidenceNote}",
                            style = MaterialTheme.typography.labelSmall,
                            color = FvgDeepGreen,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                FvgField(vName, { vName = it }, "Vendor Name")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(dDate, { dDate = it }, "Date", modifier = Modifier.weight(1f))
                    FvgField(dCurr, { dCurr = it }, "Currency", modifier = Modifier.weight(0.7f))
                    FvgField(dAmt, { dAmt = it }, "Total", numeric = true, modifier = Modifier.weight(1f))
                }
                FvgField(dSum, { dSum = it }, "Extracted Line Summary", singleLine = false)
                if (draft.reviewStatus == "pending_review" && state.canEditFinancialData) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgPrimaryButton(
                            text = if (isArabic) "اعتماد المسودة" else "Verify Draft",
                            onClick = { viewModel.reviewOcrDraft(draft, vName, dDate, dCurr, dAmt, dSum, "verified") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = { viewModel.reviewOcrDraft(draft, vName, dDate, dCurr, dAmt, dSum, "rejected") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) { Text(if (isArabic) "رفض" else "Reject") }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrgRbacAuditSettingsTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    isDark: Boolean,
    onToggleLanguage: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenDeveloperIntro: () -> Unit,
    onSignOutClick: () -> Unit
) {
    val org = state.activeOrg
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var editOrgName by remember(org?.id) { mutableStateOf(org?.name ?: "") }
    var editCurrency by remember(org?.id) { mutableStateOf(org?.baseCurrency ?: "SDG") }
    var editPlan by remember(org?.id) { mutableStateOf(org?.planTier ?: "trial") }
    var inviteUid by remember { mutableStateOf("") }
    var inviteRole by remember { mutableStateOf("accountant") }
    var newWorkspaceName by remember { mutableStateOf("") }

    // Manual Sudan Payment Proof submission state
    var payPlanTier by remember { mutableStateOf("professional") }
    var payMethod by remember { mutableStateOf("bankak") }
    var payAmount by remember { mutableStateOf("29.00") }
    var payCurrency by remember { mutableStateOf("USD") }
    var senderName by remember { mutableStateOf("") }
    var referenceNo by remember { mutableStateOf("") }
    var transferDate by remember { mutableStateOf(today) }
    var payNotes by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.organizations.size > 1) {
            item {
                FvgCard {
                    Text(if (isArabic) "تبديل المنظمة النشطة" else "Switch Active Workspace", style = MaterialTheme.typography.titleMedium)
                    FvgChipSelector(
                        options = state.organizations.map { it.id },
                        selected = org?.id ?: "",
                        onSelect = { viewModel.switchOrganization(it) },
                        labelResolver = { id -> state.organizations.firstOrNull { it.id == id }?.name ?: id }
                    )
                }
            }
        }

        if (org != null) {
            item {
                FvgTrialCountdownBanner(
                    trialEndsAtMillis = state.effectiveTrialEndsMillis(),
                    isArabic = isArabic,
                    isPreview72h = false,
                    serverAnchorMillis = state.serverAnchorMillis,
                    elapsedBaselineMillis = state.elapsedBaselineRealtimeMillis
                )
            }

            // Manual Sudan Payment Proof Submission + Visa Card Adapter Notice
            item {
                FvgCard(containerColor = FvgSoftCream) {
                    Text(
                        text = if (isArabic) "تقديم إثبات الدفع (بنكك / أوكاش / تحويل بنكي)" else "Submit Payment Proof (Bankak / O-Cash / Bank Transfer)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = FvgDeepGreen
                    )
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (isArabic)
                                "ملاحظة بطاقة Visa/MasterCard: بوابة البطاقات المباشرة غير مفعّلة هنا (Adapter Notice — لا يتم جمع أرقام البطاقات أو CVV). لتفعيل الباقة الآن يُرجى التحويل عبر بنكك أو أوكاش أو تحويل بنكي ورفع رقم المرجع للمراجعة."
                            else
                                "Visa/MasterCard Adapter Notice: Live online card gateway is not configured here (zero card/CVV fields). Submit a verified Bankak, O-Cash, or Bank Transfer receipt reference below for admin activation.",
                            style = MaterialTheme.typography.labelSmall,
                            color = FvgDeepGreen,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    FvgChipSelector(
                        options = listOf("starter", "professional", "enterprise"),
                        selected = payPlanTier,
                        onSelect = {
                            payPlanTier = it
                            payAmount = when (it) {
                                "starter" -> "12.00"
                                "professional" -> "29.00"
                                else -> "79.00"
                            }
                        }
                    )
                    FvgChipSelector(
                        options = listOf("bankak", "ocash", "bank_transfer"),
                        selected = payMethod,
                        onSelect = { payMethod = it },
                        labelResolver = { m ->
                            when (m) {
                                "bankak" -> if (isArabic) "بنكك (Bankak)" else "Bankak"
                                "ocash" -> if (isArabic) "أوكاش (O-Cash)" else "O-Cash"
                                else -> if (isArabic) "تحويل بنكي" else "Bank Transfer"
                            }
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgField(payAmount, { payAmount = it }, "Amount", numeric = true, modifier = Modifier.weight(1f), testTag = "payment_amount_input")
                        FvgField(payCurrency, { payCurrency = it }, "Currency", modifier = Modifier.weight(0.7f))
                        FvgField(transferDate, { transferDate = it }, "Date", modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgField(senderName, { senderName = it }, if (isArabic) "اسم المحوّل" else "Sender Name", modifier = Modifier.weight(1f), testTag = "payment_sender_input")
                        FvgField(referenceNo, { referenceNo = it }, if (isArabic) "رقم المرجع" else "Reference No", modifier = Modifier.weight(1f), testTag = "payment_ref_input")
                    }
                    FvgField(payNotes, { payNotes = it }, if (isArabic) "ملاحظات التحويل" else "Transfer Notes")
                    FvgGoldButton(
                        text = if (isArabic) "إرسال إثبات الدفع للمراجعة" else "Submit Payment Proof [Pending Verification]",
                        icon = Icons.Filled.ReceiptLong,
                        enabled = state.canAdminOrg && senderName.isNotBlank() && referenceNo.trim().length >= 2 && !state.isBusy,
                        onClick = {
                            viewModel.submitPaymentRequest(payPlanTier, payMethod, payAmount, payCurrency, senderName, referenceNo, transferDate, payNotes)
                            senderName = ""; referenceNo = ""; payNotes = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "submit_payment_proof_button"
                    )
                }
            }

            // Payment Requests History & Admin Verification Queue
            if (state.paymentRequests.isNotEmpty()) {
                items(state.paymentRequests.reversed(), key = { it.id }) { req ->
                    FvgCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${req.paymentMethod.uppercase()} • ${req.referenceNo}", style = MaterialTheme.typography.titleMedium, color = FvgDeepGreen)
                            FvgRoleBadge(req.status)
                        }
                        Text("Plan: ${req.planTier} | Amount: ${req.amount} ${req.currency} | Sender: ${req.senderName} (${req.transferDate})", style = MaterialTheme.typography.bodyMedium)
                        if (req.reviewNotes.isNotBlank()) {
                            Text("Review Note: ${req.reviewNotes}", style = MaterialTheme.typography.labelSmall, color = FvgAntiqueGold)
                        }
                        if (req.status == "pending_verification" && state.canAdminOrg) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FvgPrimaryButton(
                                    text = if (isArabic) "اعتماد وتفعيل الباقة" else "Approve & Activate Plan",
                                    onClick = { viewModel.reviewPaymentRequest(req, "approved", "Verified manual transfer ref ${req.referenceNo}") },
                                    modifier = Modifier.weight(1.2f),
                                    testTag = "approve_payment_button"
                                )
                                OutlinedButton(
                                    onClick = { viewModel.reviewPaymentRequest(req, "rejected", "Reference could not be verified") },
                                    modifier = Modifier.weight(0.7f).heightIn(min = 48.dp)
                                ) {
                                    Text(if (isArabic) "رفض" else "Reject", color = FvgNegative)
                                }
                            }
                        }
                    }
                }
            }

            item {
                FvgCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isArabic) "إعدادات المنظمة والاشتراك" else "Organization & Subscription Settings", style = MaterialTheme.typography.titleMedium)
                        FvgRoleBadge(org.subscriptionStatus)
                    }
                    FvgField(editOrgName, { editOrgName = it }, "Organization Name")
                    FvgChipSelector(listOf("SDG", "USD", "SAR", "AED", "EUR"), editCurrency, { editCurrency = it })
                    FvgChipSelector(listOf("trial", "starter", "professional", "enterprise"), editPlan, { editPlan = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgPrimaryButton(
                            text = if (isArabic) "حفظ الإعدادات" else "Save Settings",
                            enabled = state.canAdminOrg && editOrgName.isNotBlank() && !state.isBusy,
                            onClick = {
                                val nextStatus = if (org.subscriptionStatus == "active") "active" else org.subscriptionStatus
                                viewModel.updateOrganizationSettings(editOrgName, editCurrency, editPlan, nextStatus)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            enabled = state.canAdminOrg && org.subscriptionStatus != "cancelled" && !state.isBusy,
                            onClick = {
                                viewModel.updateOrganizationSettings(editOrgName, editCurrency, editPlan, "cancelled")
                            },
                            modifier = Modifier.weight(0.9f).heightIn(min = 48.dp)
                        ) {
                            Text(if (isArabic) "إلغاء الاشتراك" else "Cancel Plan")
                        }
                    }
                }
            }

            if (state.canAdminOrg) {
                item {
                    FvgCard {
                        Text(if (isArabic) "إدارة أعضاء المنظمة ومصفوفة الصلاحيات (RBAC)" else "Team, Role Access & Permissions Matrix (RBAC)", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Owner/Admin: Full write, Approvals, Role & Plan control | Accountant: Journals, Costing, Prices, Inventory | Viewer: Read-only",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FvgField(inviteUid, { inviteUid = it }, "Member Firebase UID")
                        FvgChipSelector(listOf("admin", "accountant", "viewer"), inviteRole, { inviteRole = it })
                        FvgGoldButton(
                            text = if (isArabic) "تحديث صلاحية العضو" else "Grant / Update Member Role",
                            enabled = inviteUid.isNotBlank() && !state.isBusy,
                            onClick = {
                                viewModel.updateMemberRole(inviteUid, inviteRole)
                                inviteUid = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        item {
            FvgCard {
                Text(if (isArabic) "إنشاء مساحة عمل إضافية" else "Create Additional Organization", style = MaterialTheme.typography.titleMedium)
                FvgField(newWorkspaceName, { newWorkspaceName = it }, "New Organization Name")
                FvgGoldButton(
                    text = if (isArabic) "إنشاء منظمة جديدة (تجربة 3 أيام / SDG)" else "Create Workspace (3-Day Trial)",
                    enabled = newWorkspaceName.isNotBlank() && !state.isBusy,
                    onClick = {
                        viewModel.createOrganization(newWorkspaceName, "SDG")
                        newWorkspaceName = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            FvgCard {
                Text(if (isArabic) "التفضيلات والحساب والمطورة" else "Preferences, Vision & Account", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onToggleLanguage, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(if (isArabic) "Switch to English" else "التبديل إلى العربية")
                    }
                    OutlinedButton(onClick = onToggleTheme, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(if (isDark) "Light Theme" else "Dark Theme")
                    }
                }
                OutlinedButton(
                    onClick = onOpenDeveloperIntro,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isArabic) "عرض صفحة المطوّرة زهراء يوسف" else "View Researcher & FVG Vision (Zahra Yousif)")
                }
                Button(
                    onClick = onSignOutClick,
                    colors = ButtonDefaults.buttonColors(containerColor = FvgNegative, contentColor = MaterialTheme.colorScheme.onError),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(fvgStrings(isArabic).signOut)
                }
            }
        }

        item {
            Text(
                text = if (isArabic) "سجل التدقيق غير القابل للتعديل (${state.auditLogs.size})" else "Immutable Audit Trail (${state.auditLogs.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }

        items(state.auditLogs.reversed().take(30), key = { it.id }) { log ->
            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(log.action, style = MaterialTheme.typography.labelLarge, color = FvgDeepGreen)
                    Text(log.entityType, style = MaterialTheme.typography.labelSmall, color = FvgAntiqueGold)
                }
                Text(log.summary, style = MaterialTheme.typography.bodyMedium)
                Text("By: ${log.actorEmail}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
