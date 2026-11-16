package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.*
import com.example.ui.i18n.fvgStrings
import com.example.ui.theme.*
import com.example.ui.viewmodel.FvgUiState
import com.example.ui.viewmodel.FvgViewModel

@Composable
fun ResearchAndBenAlZahraScreen(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    isDark: Boolean,
    onToggleLanguage: () -> Unit,
    onToggleTheme: () -> Unit
) {
    val s = fvgStrings(isArabic)
    var subTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().testTag("research_ben_alzahra_screen")) {
        FvgTopHeaderBanner(
            title = if (isArabic) "البحث الأكاديمي وبن الزهراء" else "Nobo Thesis & Ben Al-Zahra",
            subtitle = s.brandTaglineEn,
            icon = Icons.Filled.School,
            isArabic = isArabic,
            isDark = isDark,
            onToggleLanguage = onToggleLanguage,
            onToggleTheme = onToggleTheme,
            orgName = state.activeOrg?.name,
            roleBadge = state.currentUserRole
        )
        FvgSubTabRow(
            tabs = listOf(s.tabResearchNobo, s.tabBenAlZahra),
            selectedIndex = subTab,
            onSelect = { subTab = it },
            testTagPrefix = "research"
        )

        if (subTab == 0) {
            NoboAcademicThesisTab(state = state, viewModel = viewModel, isArabic = isArabic)
        } else {
            BenAlZahraShowcaseTab(isArabic = isArabic)
        }
    }
}

@Composable
private fun NoboAcademicThesisTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean
) {
    val s = fvgStrings(isArabic)
    var editingId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var recordType by remember { mutableStateOf("hypothesis") }
    var dataStatus by remember { mutableStateOf("missing") }
    var summary by remember { mutableStateOf("") }
    var sourceRef by remember { mutableStateOf("Nile University 2026 — Nobo Field Study") }
    var periodTag by remember { mutableStateOf("2026") }

    val recordTypes = listOf("question", "methodology", "hypothesis", "evidence", "dictionary", "note")
    val statuses = listOf("available", "partial", "missing")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Thesis Identity & Supervision Header
        item {
            FvgCard(containerColor = FvgSoftCream) {
                Text(
                    text = if (isArabic)
                        "دور أساليب المحاسبة الإدارية في تسعير المنتجات الصناعية في ظل بيئة تضخمية: دراسة ميدانية على مصنع نوبو بالخرطوم بحري، السودان"
                    else
                        "The Role of Management Accounting Techniques in Pricing Industrial Products in an Inflationary Environment: A Field Study of Nobo Factory in Khartoum North, Sudan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FvgDeepGreen
                )
                HorizontalDivider(color = FvgWarmGold.copy(alpha = 0.4f))
                Text(
                    text = if (isArabic)
                        "الباحثة: ${s.ownerFullName}\nالمشرف الأكاديمي: ${s.supervisorName}\nجامعة النيل — كلية إدارة الأعمال — تخصص المحاسبة — 2026"
                    else
                        "Researcher: ${s.ownerFullName}\nAcademic Supervisor: ${s.supervisorName}\nNile University — Faculty of Business Administration — Accounting — 2026",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FvgInkDark
                )
            }
        }

        // Strict Zero-Fabrication & Isolation Notice
        item {
            FvgCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.MenuBook, contentDescription = null, tint = FvgAntiqueGold)
                    Text(
                        text = if (isArabic) "سياسة النزاهة الأكاديمية وعدم اختلاق البيانات [Pending Field Data]" else "Academic Integrity & Zero-Fabrication Notice [Pending Field Data]",
                        style = MaterialTheme.typography.titleMedium,
                        color = FvgDeepGreen
                    )
                }
                Text(
                    text = if (isArabic)
                        "يعرض هذا القسم الهيكل البحثي الكامل لدراسة مصنع نوبو دون أي استبيانات مختلقة أو نتائج ميدانية مفبركة. تبقى المؤشرات التجريبية المصنّفة موسومة بحالة [Pending Field Data] حتى تُدخل الباحثة السجلات الفعلية الموثقة، كما يُفصل تماماً عن العلامة التجارية المستقلة (بن الزهراء)."
                    else
                        "This academic workspace strictly preserves research structure without fabricated sample sizes, questionnaire responses, or invented Nobo factory costs. Uncollected empirical sections remain labeled [Pending Field Data] until verified records are added by the researcher, and are completely isolated from Ben Al-Zahra.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FvgMutedForest
                )
            }
        }

        // Methodology, Hypotheses & Glossary Reference Cards
        item {
            FvgCard {
                Text(
                    text = if (isArabic) "إطار المنهجية والفرضيات [Pending Field Data]" else "Methodology & Hypotheses Structure [Pending Field Data]",
                    style = MaterialTheme.typography.titleMedium,
                    color = FvgDeepGreen
                )
                listOf(
                    "H1 [Pending Field Data]" to (if (isArabic) "أثر أساليب المحاسبة الإدارية (الفعلية، المعيارية، المستهدفة، ABC) على قرارات التسعير في مصنع نوبو بالخرطوم بحري." else "Impact of management accounting techniques (Actual, Standard, Target, ABC) on product pricing accuracy under inflation."),
                    "H2 [Pending Field Data]" to (if (isArabic) "العلاقة بين رصد التضخم وسعر الصرف وتعديل تكلفة الاستبدال لحماية هامش الربح." else "Relationship between inflation index monitoring, FX pass-through, and replacement-cost margin protection."),
                    "H3 [Pending Field Data]" to (if (isArabic) "فاعلية تحليل التعادل والانحرافات ومحرك المخاطر في ترشيد القرارات الإنتاجية." else "Effectiveness of CVP break-even, variance decomposition, and deterministic risk scoring for industrial managers.")
                ).forEach { (hTitle, hDesc) ->
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(hTitle, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = FvgDeepGreen)
                            Text(hDesc, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                HorizontalDivider()
                Text(
                    text = if (isArabic) "مسرد مصطلحات المحاسبة الإدارية" else "Management Accounting Terminology Dictionary",
                    style = MaterialTheme.typography.labelLarge,
                    color = FvgAntiqueGold
                )
                Text(
                    text = if (isArabic)
                        "• تكلفة الاستبدال: تكلفة إعادة شراء/تصنيع المخزون بالسعر السوقي الجاري منفصلةً عن القيمة الدفترية.\n• نقطة التعادل: Fixed Costs / (Selling Price - Variable Cost/Unit) مقربةً للأعلى (CEILING).\n• التسعير المستهدف: Target Allowable Cost = Selling Price × (1 - Target Margin %)."
                    else
                        "• Replacement Cost: Current market cost to replenish stock, kept separate from accounting FIFO/MWA book value.\n• Break-Even Units: Fixed Costs / (Price - Unit Variable Cost), rounded up via CEILING.\n• Target Costing: Target Allowable Cost = Selling Price × (1 - Target Margin %).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Research Record CRUD Form
        item {
            FvgCard {
                Text(
                    text = if (editingId == null) {
                        if (isArabic) "إضافة سجل بحثي / فرضية / دليل ميداني" else "Add Thesis Question, Hypothesis or Field Evidence"
                    } else {
                        if (isArabic) "تعديل السجل البحثي" else "Edit Research Record"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = FvgDeepGreen
                )
                FvgChipSelector(
                    options = recordTypes,
                    selected = recordType,
                    onSelect = { recordType = it }
                )
                FvgChipSelector(
                    options = statuses,
                    selected = dataStatus,
                    onSelect = { dataStatus = it },
                    labelResolver = { st ->
                        when (st) {
                            "available" -> if (isArabic) "متاح (Available)" else "Available"
                            "partial" -> if (isArabic) "جزئي (Partial)" else "Partial"
                            else -> if (isArabic) "مفقود (Pending Data)" else "Missing [Pending]"
                        }
                    }
                )
                FvgField(
                    value = title,
                    onValueChange = { title = it },
                    label = if (isArabic) "عنوان السجل البحثي" else "Research Record Title",
                    testTag = "research_title_input"
                )
                FvgField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = if (isArabic) "المحتوى / الملاحظة / نتيجة القياس" else "Summary / Finding / Pending Note",
                    singleLine = false,
                    testTag = "research_summary_input"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(sourceRef, { sourceRef = it }, if (isArabic) "المرجع / المصدر" else "Source Ref", modifier = Modifier.weight(1.3f))
                    FvgField(periodTag, { periodTag = it }, if (isArabic) "الفترة" else "Period", modifier = Modifier.weight(0.7f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgPrimaryButton(
                        text = if (editingId == null) (if (isArabic) "حفظ السجل" else "Save Research Record") else (if (isArabic) "تحديث السجل" else "Update Record"),
                        icon = Icons.Filled.AddCircle,
                        enabled = state.canEditFinancialData && title.isNotBlank() && summary.isNotBlank() && !state.isBusy,
                        onClick = {
                            viewModel.saveResearchRecord(editingId, title, recordType, summary, dataStatus, sourceRef, periodTag)
                            editingId = null
                            title = ""
                            summary = ""
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "save_research_button"
                    )
                    if (editingId != null) {
                        OutlinedButton(
                            onClick = {
                                editingId = null
                                title = ""
                                summary = ""
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(s.cancel)
                        }
                    }
                }
            }
        }

        if (state.researchRecords.isEmpty()) {
            item {
                FvgEmptyStateCard(
                    if (isArabic) "لا توجد سجلات بحثية مدخلة في هذه المنظمة بعد — كافة نتائج نوبو محفوظة كإطار خالٍ من التزوير." else "No empirical research entries recorded yet — Nobo case remains strictly free of fabricated results."
                )
            }
        } else {
            items(state.researchRecords, key = { it.id }) { rec ->
                FvgCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(rec.title, style = MaterialTheme.typography.titleMedium, color = FvgDeepGreen, modifier = Modifier.weight(1f))
                        FvgRoleBadge("${rec.recordType} • ${rec.dataStatus}")
                    }
                    Text(rec.summary, style = MaterialTheme.typography.bodyMedium)
                    Text("${rec.sourceRef} • ${rec.periodTag}", style = MaterialTheme.typography.labelSmall, color = FvgAntiqueGold)
                    if (state.canEditFinancialData) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    editingId = rec.id
                                    title = rec.title
                                    recordType = rec.recordType
                                    dataStatus = rec.dataStatus
                                    summary = rec.summary
                                    sourceRef = rec.sourceRef
                                    periodTag = rec.periodTag
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit research record", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isArabic) "تعديل" else "Edit")
                            }
                            OutlinedButton(
                                onClick = { viewModel.deleteResearchRecord(rec.id) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete research record", tint = FvgNegative, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isArabic) "حذف" else "Delete", color = FvgNegative)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BenAlZahraShowcaseTab(isArabic: Boolean) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("ben_alzahra_showcase_tab"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FvgCard(containerColor = FvgSoftCream) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.Coffee, contentDescription = null, tint = FvgDeepGreen)
                    Column {
                        Text(
                            text = if (isArabic) "مشروع بن الزهراء التجاري (منفصل تماماً عن نوبو)" else "Ben Al-Zahra Commercial Project (Isolated Showcase)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = FvgDeepGreen
                        )
                        Text(
                            text = if (isArabic) "مرجع تجاري مستقل للتسعير وتكاليف التحميص والتعبئة" else "Independent commercial roasting & packaging costing case reference",
                            style = MaterialTheme.typography.labelSmall,
                            color = FvgAntiqueGold
                        )
                    }
                }
                Image(
                    painter = painterResource(id = R.drawable.fvg_ben_alzahra_products),
                    contentDescription = "Ben Al-Zahra Coffee Products Showcase",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                Text(
                    text = if (isArabic)
                        "يمثل مشروع بن الزهراء علامة تجارية مستقلة داخل منصة FVG لتطبيق نماذج تسعير المنتجات التحويلية (المواد الخام، التحميص، التعبئة، الطاقة، والنقل) تحت التضخم، ولا يختلط محاسبياً أو إحصائياً ببيانات الدراسة الأكاديمية لمصنع نوبو."
                    else
                        "Ben Al-Zahra operates as an independent commercial project reference demonstrating multi-component BOM costing (green beans, roasting energy, packaging, distribution, and margin protection under inflation). Its data and brand identity remain strictly isolated from the Nobo academic thesis case.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FvgInkDark
                )
            }
        }

        item {
            FvgCard {
                Text(
                    text = if (isArabic) "خطوط التكلفة المطبّقة في المنصة (دون أرقام مفترضة)" else "Supported Commercial Costing Structure (Create Org to Enter Live Numbers)",
                    style = MaterialTheme.typography.titleMedium,
                    color = FvgDeepGreen
                )
                listOf(
                    if (isArabic) "• مدخلات البن الأخضر والعبوات والملصقات عبر مراقبة الأسعار والـ FIFO/MWA." else "• Green bean inputs & packaging tracked via Price Registry and FIFO/Moving-Average Inventory.",
                    if (isArabic) "• توزيع تكاليف التحميص والطاقة والعمالة المباشرة عبر الأساليب الـ 8 (ABC / Actual / Target)." else "• Roasting utilities, direct labor, and overhead allocated via the 8-method Costing Engine (ABC / Target / Process).",
                    if (isArabic) "• اختبار صدمات الصرف والتضخم عبر محاكي ماذا-لو لتحديد نقطة التعادل والسعر المقترح." else "• FX & inflation sensitivity testing via the What-If Simulator with Break-Even CEILING output."
                ).forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
