package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.FvgAiRepository
import com.example.data.repository.FvgRepository
import com.example.domain.engine.FvgFinancialEngine
import com.example.domain.export.ReportExportPayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FvgNotificationAlert(
    val id: String,
    val titleEn: String,
    val titleAr: String,
    val detailEn: String,
    val detailAr: String,
    val severity: String, // "critical", "warning", "info"
    val targetTab: Int
)

data class FvgUiState(
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val userProfile: UserProfile? = null,
    val organizations: List<Organization> = emptyList(),
    val activeOrg: Organization? = null,
    val products: List<Product> = emptyList(),
    val priceObservations: List<PriceObservation> = emptyList(),
    val inflationRecords: List<InflationRecord> = emptyList(),
    val priceBaskets: List<PriceBasket> = emptyList(),
    val fxRates: List<FxRate> = emptyList(),
    val costSnapshots: List<CostSnapshot> = emptyList(),
    val standardCosts: List<StandardCostSheet> = emptyList(),
    val productionOrders: List<ProductionOrderRecord> = emptyList(),
    val scenarios: List<ScenarioRecord> = emptyList(),
    val accounts: List<AccountRecord> = emptyList(),
    val journalEntries: List<JournalEntryRecord> = emptyList(),
    val counterpartyItems: List<CounterpartyRecord> = emptyList(),
    val budgets: List<BudgetRecord> = emptyList(),
    val inventoryItems: List<InventoryItemRecord> = emptyList(),
    val inventoryMovements: List<InventoryMovementRecord> = emptyList(),
    val stocktakes: List<StocktakeSessionRecord> = emptyList(),
    val scrapLosses: List<ScrapLossRecord> = emptyList(),
    val ocrDrafts: List<OcrDraftRecord> = emptyList(),
    val auditLogs: List<AuditLogRecord> = emptyList(),
    val paymentRequests: List<PaymentRequestRecord> = emptyList(),
    val researchRecords: List<ResearchRecord> = emptyList(),
    val statementsReport: FvgFinancialEngine.FinancialStatementsReport? = null,
    val financialRatios: List<FvgFinancialEngine.FinancialRatioResult> = emptyList(),
    val csvPreviewRows: List<FvgFinancialEngine.CsvProductRow> = emptyList(),
    val accountingCsvPreviewRows: List<FvgFinancialEngine.AccountingCsvRow> = emptyList(),
    val aiMode: String = "financial", // "general", "financial", "research"
    val aiResponse: String = "",
    val chatTranscript: List<Pair<String, String>> = emptyList(),
    val isAiStreaming: Boolean = false,
    val isOcrExtracting: Boolean = false,
    val serverClockOffsetMillis: Long = 0L,
    val serverAnchorMillis: Long = System.currentTimeMillis(),
    val elapsedBaselineRealtimeMillis: Long = SystemClock.elapsedRealtime(),
    val statusMessage: String? = null,
    val errorMessage: String? = null
) {
    val currentUserRole: String
        get() = activeOrg?.let { org ->
            val uid = userProfile?.userId.orEmpty()
            org.roles[uid] ?: if (org.ownerId == uid) "owner" else "viewer"
        } ?: "viewer"

    val canEditFinancialData: Boolean
        get() = currentUserRole in setOf("owner", "admin", "accountant")

    val canAdminOrg: Boolean
        get() = currentUserRole in setOf("owner", "admin")

    val totalInventoryBookValue: BigDecimal
        get() = inventoryItems.fold(BigDecimal.ZERO) { acc, item ->
            acc.add(FvgFinancialEngine.dOrZero(item.totalBookValue))
        }

    val totalReplacementCost: BigDecimal
        get() = inventoryItems.fold(BigDecimal.ZERO) { acc, item ->
            val qty = FvgFinancialEngine.dOrZero(item.quantityOnHand)
            val mkt = FvgFinancialEngine.dOrZero(item.currentMarketPrice)
            val book = FvgFinancialEngine.dOrZero(item.totalBookValue)
            acc.add(if (mkt > BigDecimal.ZERO) qty.multiply(mkt) else book)
        }

    fun effectiveTrialEndsMillis(): Long? =
        FvgFinancialEngine.computeServerSyncedTrialEndMillis(
            userTrialStartMillis = userProfile?.trialStartedAt?.toDate()?.time,
            orgTrialEndsAtMillis = activeOrg?.trialEndsAt?.toDate()?.time,
            durationHours = activeOrg?.trialDurationHours ?: userProfile?.trialDurationHours ?: FvgFinancialEngine.TRIAL_DURATION_HOURS
        )

    fun estimatedServerNowMillis(): Long =
        serverAnchorMillis + (SystemClock.elapsedRealtime() - elapsedBaselineRealtimeMillis).coerceAtLeast(0L)

    fun isTrialExpiredAndLocked(): Boolean {
        val org = activeOrg ?: return false
        if (org.subscriptionStatus == "active") return false
        if (org.subscriptionStatus == "expired" || org.subscriptionStatus == "cancelled") return true
        val endsMillis = effectiveTrialEndsMillis() ?: return false
        return estimatedServerNowMillis() >= endsMillis
    }

    fun buildNotifications(): List<FvgNotificationAlert> {
        val nowMillis = estimatedServerNowMillis()
        val alerts = mutableListOf<FvgNotificationAlert>()
        val org = activeOrg
        if (org != null && (org.subscriptionStatus == "trial" || org.subscriptionStatus == "expired")) {
            val endsMillis = effectiveTrialEndsMillis()
            if (org.subscriptionStatus == "expired") {
                alerts.add(
                    FvgNotificationAlert(
                        id = "trial_expired",
                        titleEn = "3-Day Trial Expired",
                        titleAr = "انتهت الفترة التجريبية (3 أيام)",
                        detailEn = "Your 72-hour trial has ended. Submit a Bankak / O-Cash / Bank Transfer request in Org & Billing.",
                        detailAr = "انتهت مدة التجربة (72 ساعة). قدّم إثبات الدفع عبر بنكك / أوكاش / تحويل بنكي في إعدادات المنظمة.",
                        severity = "critical",
                        targetTab = 6
                    )
                )
            } else if (endsMillis != null) {
                val cd = FvgFinancialEngine.computeTrialCountdown(endsMillis, nowMillis)
                if (cd.isExpired) {
                    alerts.add(
                        FvgNotificationAlert(
                            id = "trial_expired",
                            titleEn = "3-Day Trial Expired",
                            titleAr = "انتهت الفترة التجريبية (3 أيام)",
                            detailEn = "Your 72-hour trial has ended. Review plan & Sudan payment proof options in Org & Billing.",
                            detailAr = "انتهت مدة التجربة (72 ساعة). راجع خيارات الباقة والدفع في إعدادات المنظمة.",
                            severity = "critical",
                            targetTab = 6
                        )
                    )
                } else {
                    alerts.add(
                        FvgNotificationAlert(
                            id = "trial_countdown",
                            titleEn = "Trial Active: ${cd.days}d ${cd.hours}h left",
                            titleAr = "التجربة نشطة: متبقي ${cd.days}ي ${cd.hours}س",
                            detailEn = "Full 72-hour server-synced trial ends in ${cd.days}d ${cd.hours}h ${cd.minutes}m ${cd.seconds}s.",
                            detailAr = "تنتهي الفترة التجريبية خلال ${cd.days} يوم و ${cd.hours} ساعة و ${cd.minutes} دقيقة.",
                            severity = if (cd.totalRemainingHours < 24) "warning" else "info",
                            targetTab = 6
                        )
                    )
                }
            }
        }
        val spikes = priceObservations.filter {
            FvgFinancialEngine.isUnusualPriceMovement(runCatching { FvgFinancialEngine.d(it.pctChange) }.getOrNull())
        }
        if (spikes.isNotEmpty()) {
            val latest = spikes.last()
            alerts.add(
                FvgNotificationAlert(
                    id = "price_spike_${latest.id}",
                    titleEn = "Unusual Price Movement (${spikes.size})",
                    titleAr = "حركة سعر غير معتادة (${spikes.size})",
                    detailEn = "${latest.productName} moved ${latest.pctChange}% (${latest.previousPrice} -> ${latest.price} ${latest.currency}).",
                    detailAr = "تغير سعر ${latest.productName} بنسبة ${latest.pctChange}%.",
                    severity = "warning",
                    targetTab = 1
                )
            )
        }
        val aging = FvgFinancialEngine.computeArApAgingBuckets(
            counterpartyItems.map {
                val rem = FvgFinancialEngine.dOrZero(it.amount).subtract(FvgFinancialEngine.dOrZero(it.paidAmount))
                Triple(it.dueDate, rem, it.status)
            },
            nowMillis / 86400_000L
        )
        if (aging.overdueCount > 0) {
            alerts.add(
                FvgNotificationAlert(
                    id = "overdue_arap",
                    titleEn = "${aging.overdueCount} Overdue AR/AP Invoices",
                    titleAr = "${aging.overdueCount} فواتير ذمم متأخرة",
                    detailEn = "Review counterparty aging buckets in Ledger -> AR / AP.",
                    detailAr = "راجع أعمار الديون والفواتير المستحقة في تبويب المحاسبة.",
                    severity = "critical",
                    targetTab = 3
                )
            )
        }
        val pendingScrap = scrapLosses.count { it.status == "pending_approval" }
        if (pendingScrap > 0) {
            alerts.add(
                FvgNotificationAlert(
                    id = "pending_scrap",
                    titleEn = "$pendingScrap Pending Scrap / Loss Approvals",
                    titleAr = "$pendingScrap طلبات هدر وتلف بانتظار الاعتماد",
                    detailEn = "Approve or reject scrap requests to adjust stock & loss ledger.",
                    detailAr = "اعتمد أو ارفض طلبات التوالف لترحيل الأثر المخزني والمحاسبي.",
                    severity = "warning",
                    targetTab = 4
                )
            )
        }
        val pendingPayments = paymentRequests.count { it.status == "pending_verification" }
        if (pendingPayments > 0) {
            alerts.add(
                FvgNotificationAlert(
                    id = "pending_payments",
                    titleEn = "$pendingPayments Payment Proofs Pending Verification",
                    titleAr = "$pendingPayments إثباتات دفع بانتظار التحقق",
                    detailEn = "Verify Bankak / O-Cash / Bank Transfer proofs in Org & Billing.",
                    detailAr = "تحقق من إيصالات بنكك / أوكاش / التحويل البنكي في إعدادات المنظمة.",
                    severity = "warning",
                    targetTab = 6
                )
            )
        }
        val pendingStocktakes = stocktakes.count { it.status == "in_review" }
        if (pendingStocktakes > 0) {
            alerts.add(
                FvgNotificationAlert(
                    id = "pending_stocktakes",
                    titleEn = "$pendingStocktakes Stocktake Sessions In Review",
                    titleAr = "$pendingStocktakes جلسات جرد بانتظار الاعتماد",
                    detailEn = "Finalize physical stocktake discrepancy postings in Stock.",
                    detailAr = "قم بمراجعة واعتماد فروقات الجرد الفعلي في تبويب المخزون.",
                    severity = "info",
                    targetTab = 4
                )
            )
        }
        val pendingOcr = ocrDrafts.count { it.reviewStatus == "pending_review" }
        if (pendingOcr > 0) {
            alerts.add(
                FvgNotificationAlert(
                    id = "pending_ocr",
                    titleEn = "$pendingOcr OCR Drafts Pending Review",
                    titleAr = "$pendingOcr مسودات فواتير OCR بانتظار المراجعة",
                    detailEn = "Verify extracted vendor & amount fields in AI & Org.",
                    detailAr = "تحقق من حقول المورد والمبلغ المستخرجة في قسم الذكاء والوثائق.",
                    severity = "info",
                    targetTab = 6
                )
            )
        }
        val drafts = journalEntries.count { it.status == "draft" }
        if (drafts > 0) {
            alerts.add(
                FvgNotificationAlert(
                    id = "draft_jes",
                    titleEn = "$drafts Unposted Draft Journal Entries",
                    titleAr = "$drafts قيود محاسبية مسودة غير مرحّلة",
                    detailEn = "Approve and post draft journals so they reflect on Financial Statements.",
                    detailAr = "اعتمد وترحّل المسودات لتنعكس في القوائم المالية.",
                    severity = "info",
                    targetTab = 3
                )
            )
        }
        if (statementsReport != null && !statementsReport.isAccountingEquationBalanced) {
            alerts.add(
                FvgNotificationAlert(
                    id = "eq_unbalanced",
                    titleEn = "Accounting Equation Check Attention",
                    titleAr = "تنبيه توازن المعادلة المحاسبية",
                    detailEn = "Verify opening balances & posted journal credits vs debits.",
                    detailAr = "تحقق من الأرصدة الافتتاحية وتوازن القيود المرحّلة.",
                    severity = "critical",
                    targetTab = 3
                )
            )
        }
        return alerts
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FvgViewModel(
    private val repository: FvgRepository,
    private val aiRepository: FvgAiRepository,
    val currentUserId: String,
    val userEmail: String,
    val userDisplayName: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(FvgUiState())
    val uiState: StateFlow<FvgUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(currentUserId, userEmail, userDisplayName)
                .onFailure { e -> _uiState.update { it.copy(errorMessage = e.message) } }
            val offset = repository.syncServerClockOffset(currentUserId)
            val nowBaseline = SystemClock.elapsedRealtime()
            _uiState.update {
                it.copy(
                    serverClockOffsetMillis = offset,
                    serverAnchorMillis = System.currentTimeMillis() + offset,
                    elapsedBaselineRealtimeMillis = nowBaseline
                )
            }
        }
        observeCoreWorkspace()
    }

    private fun observeCoreWorkspace() {
        val profileFlow = repository.observeUserProfile(currentUserId)
            .catch { e -> _uiState.update { it.copy(errorMessage = e.message, isLoading = false) } }

        val orgsFlow = repository.observeOrganizations(currentUserId)
            .catch { e -> _uiState.update { it.copy(errorMessage = e.message, isLoading = false) } }

        viewModelScope.launch {
            combine(profileFlow, orgsFlow) { profile, orgs ->
                val active = orgs.firstOrNull { it.id == profile?.activeOrgId } ?: orgs.firstOrNull()
                Triple(profile, orgs, active)
            }.collect { (profile, orgs, active) ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        userProfile = profile,
                        organizations = orgs,
                        activeOrg = active
                    )
                }
            }
        }

        val activeOrgIdFlow = _uiState.map { it.activeOrg?.id.orEmpty() }.distinctUntilChanged()

        viewModelScope.launch {
            activeOrgIdFlow.flatMapLatest { orgId ->
                if (orgId.isBlank()) flowOf(emptyList())
                else repository.observeProducts(orgId, currentUserId)
            }.catch { e -> _uiState.update { it.copy(errorMessage = e.message) } }
                .collect { list -> _uiState.update { it.copy(products = list) } }
        }

        viewModelScope.launch {
            activeOrgIdFlow.flatMapLatest { orgId ->
                if (orgId.isBlank()) flowOf(emptyList())
                else repository.observePriceObservations(orgId, currentUserId)
            }.catch { e -> _uiState.update { it.copy(errorMessage = e.message) } }
                .collect { list -> _uiState.update { it.copy(priceObservations = list) } }
        }

        viewModelScope.launch {
            activeOrgIdFlow.flatMapLatest { orgId ->
                if (orgId.isBlank()) flowOf(emptyList())
                else repository.observeInflationRecords(orgId, currentUserId)
            }.catch { e -> _uiState.update { it.copy(errorMessage = e.message) } }
                .collect { list -> _uiState.update { it.copy(inflationRecords = list) } }
        }

        viewModelScope.launch {
            activeOrgIdFlow.flatMapLatest { orgId ->
                if (orgId.isBlank()) flowOf(emptyList())
                else repository.observePriceBaskets(orgId, currentUserId)
            }.catch { e -> _uiState.update { it.copy(errorMessage = e.message) } }
                .collect { list -> _uiState.update { it.copy(priceBaskets = list) } }
        }

        viewModelScope.launch {
            activeOrgIdFlow.flatMapLatest { orgId ->
                if (orgId.isBlank()) flowOf(emptyList())
                else repository.observeFxRates(orgId, currentUserId)
            }.catch { e -> _uiState.update { it.copy(errorMessage = e.message) } }
                .collect { list -> _uiState.update { it.copy(fxRates = list) } }
        }

        viewModelScope.launch {
            activeOrgIdFlow.flatMapLatest { orgId ->
                if (orgId.isBlank()) flowOf(emptyList())
                else repository.observeCostSnapshots(orgId, currentUserId)
            }.catch { e -> _uiState.update { it.copy(errorMessage = e.message) } }
                .collect { list -> _uiState.update { it.copy(costSnapshots = list) } }
        }

        viewModelScope.launch {
            activeOrgIdFlow.flatMapLatest { orgId ->
                if (orgId.isBlank()) flowOf(emptyList())
                else repository.observeScenarios(orgId, currentUserId)
            }.catch { e -> _uiState.update { it.copy(errorMessage = e.message) } }
                .collect { list -> _uiState.update { it.copy(scenarios = list) } }
        }

        viewModelScope.launch {
            activeOrgIdFlow.flatMapLatest { orgId ->
                if (orgId.isBlank()) flowOf(Pair(emptyList<AccountRecord>(), emptyList<JournalEntryRecord>()))
                else combine(
                    repository.observeAccounts(orgId, currentUserId),
                    repository.observeJournalEntries(orgId, currentUserId)
                ) { accs, jes -> accs to jes }
            }.catch { e -> _uiState.update { it.copy(errorMessage = e.message) } }
                .collect { (accs, jes) ->
                    val report = computeStatements(accs, jes)
                    val intExp = report?.interestAndOther ?: BigDecimal.ZERO
                    val ratios = report?.let { FvgFinancialEngine.computeFinancialRatios(it, intExp) }.orEmpty()
                    _uiState.update {
                        it.copy(
                            accounts = accs,
                            journalEntries = jes,
                            statementsReport = report,
                            financialRatios = ratios
                        )
                    }
                }
        }

        viewModelScope.launch {
            activeOrgIdFlow.flatMapLatest { orgId ->
                if (orgId.isBlank()) flowOf(emptyList())
                else repository.observeCounterpartyItems(orgId, currentUserId)
            }.catch { e -> _uiState.update { it.copy(errorMessage = e.message) } }
                .collect { list -> _uiState.update { it.copy(counterpartyItems = list) } }
        }

        viewModelScope.launch {
            activeOrgIdFlow.flatMapLatest { orgId ->
                if (orgId.isBlank()) flowOf(emptyList())
                else repository.observeBudgets(orgId, currentUserId)
            }.catch { e -> _uiState.update { it.copy(errorMessage = e.message) } }
                .collect { list -> _uiState.update { it.copy(budgets = list) } }
        }

        viewModelScope.launch {
            activeOrgIdFlow.flatMapLatest { orgId ->
                if (orgId.isBlank()) flowOf(emptyList())
                else repository.observeInventoryItems(orgId, currentUserId)
            }.catch { e -> _uiState.update { it.copy(errorMessage = e.message) } }
                .collect { list -> _uiState.update { it.copy(inventoryItems = list) } }
        }

        viewModelScope.launch {
            activeOrgIdFlow.flatMapLatest { orgId ->
                if (orgId.isBlank()) flowOf(emptyList())
                else repository.observeInventoryMovements(orgId, currentUserId)
            }.catch { e -> _uiState.update { it.copy(errorMessage = e.message) } }
                .collect { list -> _uiState.update { it.copy(inventoryMovements = list) } }
        }

        viewModelScope.launch {
            activeOrgIdFlow.flatMapLatest { orgId ->
                if (orgId.isBlank()) flowOf(emptyList())
                else repository.observeStocktakes(orgId, currentUserId)
            }.catch { e -> _uiState.update { it.copy(errorMessage = e.message) } }
                .collect { list -> _uiState.update { it.copy(stocktakes = list) } }
        }

        viewModelScope.launch {
            activeOrgIdFlow.flatMapLatest { orgId ->
                if (orgId.isBlank()) flowOf(emptyList())
                else repository.observeOcrDrafts(orgId, currentUserId)
            }.catch { e -> _uiState.update { it.copy(errorMessage = e.message) } }
                .collect { list -> _uiState.update { it.copy(ocrDrafts = list) } }
        }

        viewModelScope.launch {
            activeOrgIdFlow.flatMapLatest { orgId ->
                if (orgId.isBlank()) flowOf(emptyList())
                else repository.observeAuditLogs(orgId, currentUserId)
            }.catch { e -> _uiState.update { it.copy(errorMessage = e.message) } }
                .collect { list -> _uiState.update { it.copy(auditLogs = list) } }
        }

        viewModelScope.launch {
            activeOrgIdFlow.flatMapLatest { orgId ->
                if (orgId.isBlank()) flowOf(emptyList())
                else repository.observeStandardCosts(orgId, currentUserId)
            }.catch { e -> _uiState.update { it.copy(errorMessage = e.message) } }
                .collect { list -> _uiState.update { it.copy(standardCosts = list) } }
        }

        viewModelScope.launch {
            activeOrgIdFlow.flatMapLatest { orgId ->
                if (orgId.isBlank()) flowOf(emptyList())
                else repository.observeProductionOrders(orgId, currentUserId)
            }.catch { e -> _uiState.update { it.copy(errorMessage = e.message) } }
                .collect { list -> _uiState.update { it.copy(productionOrders = list) } }
        }

        viewModelScope.launch {
            activeOrgIdFlow.flatMapLatest { orgId ->
                if (orgId.isBlank()) flowOf(emptyList())
                else repository.observeScrapLosses(orgId, currentUserId)
            }.catch { e -> _uiState.update { it.copy(errorMessage = e.message) } }
                .collect { list -> _uiState.update { it.copy(scrapLosses = list) } }
        }

        viewModelScope.launch {
            activeOrgIdFlow.flatMapLatest { orgId ->
                if (orgId.isBlank()) flowOf(emptyList())
                else repository.observePaymentRequests(orgId, currentUserId)
            }.catch { e -> _uiState.update { it.copy(errorMessage = e.message) } }
                .collect { list -> _uiState.update { it.copy(paymentRequests = list) } }
        }

        viewModelScope.launch {
            activeOrgIdFlow.flatMapLatest { orgId ->
                if (orgId.isBlank()) flowOf(emptyList())
                else repository.observeResearchRecords(orgId, currentUserId)
            }.catch { e -> _uiState.update { it.copy(errorMessage = e.message) } }
                .collect { list -> _uiState.update { it.copy(researchRecords = list) } }
        }
    }

    private fun computeStatements(
        accs: List<AccountRecord>,
        jes: List<JournalEntryRecord>
    ): FvgFinancialEngine.FinancialStatementsReport? {
        if (accs.isEmpty()) return null
        val accInputs = accs.map {
            FvgFinancialEngine.AccountInput(
                code = it.code,
                name = it.name,
                accountType = it.accountType,
                subType = it.subType,
                normalBalance = it.normalBalance,
                openingBalance = FvgFinancialEngine.dOrZero(it.openingBalance)
            )
        }
        val postedLines = jes.filter { it.status == "posted" || it.status == "reversed" }.map { je ->
            je.lines.map { l ->
                FvgFinancialEngine.JournalLineInput(
                    accountCode = l["accountCode"]?.toString().orEmpty(),
                    accountName = l["accountName"]?.toString().orEmpty(),
                    debit = FvgFinancialEngine.dOrZero(l["debit"]?.toString()),
                    credit = FvgFinancialEngine.dOrZero(l["credit"]?.toString())
                )
            }
        }
        return FvgFinancialEngine.generateFinancialStatements(accInputs, postedLines)
    }

    fun clearMessages() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    private fun runMutation(successText: String, block: suspend (Organization) -> Result<*>) {
        val org = _uiState.value.activeOrg
        if (org == null) {
            _uiState.update { it.copy(errorMessage = "Please create or select an organization first.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null, statusMessage = null) }
            val res = block(org)
            res.onSuccess {
                _uiState.update { it.copy(isBusy = false, statusMessage = successText) }
            }.onFailure { e ->
                _uiState.update { it.copy(isBusy = false, errorMessage = e.message ?: "Operation failed") }
            }
        }
    }

    // --- Preferences & Organization ---

    fun createOrganization(name: String, baseCurrency: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            repository.createOrganization(currentUserId, userEmail, name, baseCurrency)
                .onSuccess {
                    _uiState.update { it.copy(isBusy = false, statusMessage = "Workspace '$name' initialized with Chart of Accounts.") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isBusy = false, errorMessage = e.message) }
                }
        }
    }

    fun switchOrganization(orgId: String) {
        viewModelScope.launch {
            repository.updateUserPreferences(currentUserId, activeOrgId = orgId)
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            repository.updateUserPreferences(currentUserId, preferredLanguage = lang)
        }
    }

    fun setDarkMode(dark: Boolean) {
        viewModelScope.launch {
            repository.updateUserPreferences(currentUserId, darkMode = dark)
        }
    }

    fun updateOrganizationSettings(name: String, currency: String, planTier: String, subStatus: String) {
        val normalized = if (subStatus == "canceled") "cancelled" else subStatus
        runMutation("Organization & subscription settings saved.") { org ->
            repository.updateOrganizationSettings(org, name, currency, planTier, normalized, currentUserId, userEmail)
        }
    }

    fun updateMemberRole(memberUid: String, newRole: String) {
        runMutation("Member role updated to $newRole.") { org ->
            repository.updateMemberRole(org, memberUid, newRole, currentUserId, userEmail)
        }
    }

    // --- Payment Requests (Bankak / O-Cash / Bank Transfer) ---

    fun submitPaymentRequest(
        planTier: String,
        paymentMethod: String,
        amount: String,
        currency: String,
        senderName: String,
        referenceNo: String,
        transferDate: String,
        notes: String
    ) {
        runMutation("Payment proof submitted for verification [pending_verification].") { org ->
            repository.submitPaymentRequest(
                org, currentUserId, userEmail, planTier, paymentMethod,
                amount, currency, senderName, referenceNo, transferDate, notes
            )
        }
    }

    fun reviewPaymentRequest(
        request: PaymentRequestRecord,
        decision: String,
        reviewNotes: String
    ) {
        runMutation("Payment request ${request.referenceNo} marked $decision.") { org ->
            repository.reviewPaymentRequest(org, currentUserId, userEmail, request, decision, reviewNotes)
        }
    }

    // --- Research Workspace (Nobo Thesis) ---

    fun saveResearchRecord(
        existingId: String?,
        title: String,
        recordType: String,
        summary: String,
        dataStatus: String,
        sourceRef: String,
        periodTag: String
    ) {
        runMutation("Research record saved ($dataStatus).") { org ->
            repository.saveResearchRecord(
                org, currentUserId, userEmail, existingId, title, recordType, summary, dataStatus, sourceRef, periodTag
            )
        }
    }

    fun deleteResearchRecord(recordId: String) {
        runMutation("Research record deleted.") { org ->
            repository.deleteResearchRecord(org, currentUserId, userEmail, recordId)
        }
    }

    // --- Prices & CSV ---

    fun recordPriceObservation(
        existingProduct: Product?,
        name: String,
        sku: String,
        category: String,
        description: String,
        unit: String,
        currency: String,
        location: String,
        source: String,
        newPrice: String,
        observationDate: String,
        notes: String,
        attachmentRef: String = ""
    ) {
        runMutation("Price observation saved & percentage delta computed.") { org ->
            repository.recordProductAndPriceObservation(
                org, currentUserId, userEmail, existingProduct, name, sku, category,
                description, unit, currency, location, source, newPrice, observationDate, notes, attachmentRef
            )
        }
    }

    fun previewCsvImport(csvText: String) {
        val existingSkus = _uiState.value.products.mapNotNull { it.sku.takeIf { s -> s.isNotBlank() } }.toSet()
        val parsed = FvgFinancialEngine.parseAndValidateProductCsv(csvText, existingSkus)
        _uiState.update {
            it.copy(
                csvPreviewRows = parsed,
                statusMessage = if (parsed.isEmpty()) null else "Parsed ${parsed.size} rows (${parsed.count { r -> r.error == null }} valid).",
                errorMessage = if (parsed.isEmpty()) "CSV must include a header row and at least 1 product row." else null
            )
        }
    }

    fun clearCsvPreview() {
        _uiState.update { it.copy(csvPreviewRows = emptyList()) }
    }

    fun commitCsvImport(observationDate: String) {
        val rows = _uiState.value.csvPreviewRows
        runMutation("Validated CSV rows imported to product price registry.") { org ->
            repository.importValidatedCsvProducts(org, currentUserId, userEmail, rows, observationDate).also { res ->
                if (res.isSuccess) _uiState.update { it.copy(csvPreviewRows = emptyList()) }
            }
        }
    }

    // --- Inflation, Baskets & FX ---

    fun saveInflation(
        period: String,
        category: String,
        monthlyRate: String,
        annualRate: String,
        indexValue: String,
        basePeriod: String,
        sourceType: String,
        sourceName: String,
        formulaNote: String
    ) {
        runMutation("Inflation record saved.") { org ->
            repository.saveInflationRecord(
                org, currentUserId, userEmail, period, category, monthlyRate,
                annualRate, indexValue, basePeriod, sourceType, sourceName, formulaNote
            )
        }
    }

    fun saveBasket(
        existing: PriceBasket?,
        name: String,
        basePeriod: String,
        currentPeriod: String,
        currency: String,
        items: List<BasketItemMap>
    ) {
        runMutation("Weighted price basket saved with snapshot.") { org ->
            repository.savePriceBasket(org, currentUserId, userEmail, existing, name, basePeriod, currentPeriod, currency, items)
        }
    }

    fun saveFxRate(
        baseCurrency: String,
        quoteCurrency: String,
        rate: String,
        effectiveDate: String,
        rateType: String,
        sourceName: String,
        notes: String
    ) {
        runMutation("Historical exchange rate saved.") { org ->
            repository.saveFxRate(org, currentUserId, userEmail, baseCurrency, quoteCurrency, rate, effectiveDate, rateType, sourceName, notes)
        }
    }

    // --- Costing (8 Methods + Risk Engine) & Scenarios ---

    fun saveCosting(
        productId: String,
        productName: String,
        period: String,
        currency: String,
        method: String,
        input: FvgFinancialEngine.CostingInput,
        completedEquivUnits: BigDecimal? = null,
        targetMarginPct: BigDecimal = BigDecimal("20")
    ) {
        val latestInf = _uiState.value.inflationRecords.firstOrNull()?.monthlyRatePct?.let {
            runCatching { FvgFinancialEngine.d(it) }.getOrNull()
        }
        runMutation("Cost calculation snapshot ($method) saved with risk assessment.") { org ->
            repository.saveCostSnapshot(
                org = org,
                actorUid = currentUserId,
                actorEmail = userEmail,
                productId = productId,
                productName = productName,
                period = period,
                currency = currency,
                costingMethod = method,
                input = input,
                latestInflationPct = latestInf,
                completedEquivUnits = completedEquivUnits,
                targetMarginPct = targetMarginPct
            )
        }
    }

    fun saveScenario(
        snapshot: CostSnapshot,
        name: String,
        matPct: String,
        labPct: String,
        ovhPct: String,
        pricePct: String,
        scenarioType: String = "Custom",
        fxPct: String = "0",
        volumePct: String = "0"
    ) {
        runMutation("What-If scenario simulated and saved.") { org ->
            repository.saveScenario(
                org = org,
                actorUid = currentUserId,
                actorEmail = userEmail,
                snapshot = snapshot,
                name = name,
                matPct = FvgFinancialEngine.dOrZero(matPct),
                labPct = FvgFinancialEngine.dOrZero(labPct),
                ovhPct = FvgFinancialEngine.dOrZero(ovhPct),
                pricePct = FvgFinancialEngine.dOrZero(pricePct),
                scenarioType = scenarioType,
                fxPct = FvgFinancialEngine.dOrZero(fxPct),
                volumePct = FvgFinancialEngine.dOrZero(volumePct)
            )
        }
    }

    fun createStandardCost(
        productId: String,
        productName: String,
        stdMaterial: String,
        stdLaborHours: String,
        stdLaborRate: String,
        stdOverhead: String,
        notes: String
    ) {
        val versions = _uiState.value.standardCosts.count { it.productName.equals(productName.trim(), ignoreCase = true) }.toLong()
        runMutation("Standard Cost sheet saved (v${versions + 1}).") { org ->
            repository.createStandardCostSheet(
                org, currentUserId, userEmail, productId, productName, versions,
                stdMaterial, stdLaborHours, stdLaborRate, stdOverhead, notes
            )
        }
    }

    fun createProductionOrder(
        productId: String,
        productName: String,
        period: String,
        plannedUnits: String,
        goodUnits: String,
        scrapUnits: String,
        actualMat: String,
        actualLab: String,
        actualOvh: String
    ) {
        runMutation("Production order recorded with stage tracking.") { org ->
            repository.createProductionOrder(
                org, currentUserId, userEmail, productId, productName, period,
                plannedUnits, goodUnits, scrapUnits, actualMat, actualLab, actualOvh,
                listOf("Material Prep", "Processing", "Quality & Packaging")
            )
        }
    }

    fun completeProductionOrder(
        order: ProductionOrderRecord,
        scrapUnits: String,
        actMat: String,
        actLab: String,
        actOvh: String
    ) {
        runMutation("Production order marked completed & variance ready.") { org ->
            repository.completeProductionOrder(org, currentUserId, userEmail, order, scrapUnits, actMat, actLab, actOvh)
        }
    }

    // --- Accounting & AR/AP ---

    fun createAccount(
        code: String,
        name: String,
        nameAr: String,
        type: String,
        subType: String,
        normalBal: String,
        openingBalance: String
    ) {
        runMutation("Account $code added to Chart of Accounts.") { org ->
            repository.createAccount(org, currentUserId, userEmail, code, name, nameAr, type, subType, normalBal, openingBalance)
        }
    }

    fun postJournalEntry(
        referenceNo: String,
        entryDate: String,
        description: String,
        currency: String,
        exchangeRate: String,
        originalCurrency: String,
        originalAmount: String,
        lines: List<FvgFinancialEngine.JournalLineInput>,
        status: String = "posted"
    ) {
        val msg = if (status == "draft") "Draft journal saved (pending posting)." else "Balanced double-entry journal posted."
        runMutation(msg) { org ->
            repository.postJournalEntry(
                org, currentUserId, userEmail, referenceNo, entryDate,
                entryDate.take(7), description, currency, exchangeRate,
                originalCurrency, originalAmount, lines, sourceModule = "manual", status = status
            )
        }
    }

    fun approveAndPostDraft(entry: JournalEntryRecord) {
        runMutation("Draft ${entry.referenceNo} posted to ledger.") { org ->
            repository.approveAndPostDraftJournalEntry(org, currentUserId, userEmail, entry)
        }
    }

    fun previewAccountingCsvImport(csvText: String) {
        val codes = _uiState.value.accounts.map { it.code }.toSet()
        val parsed = FvgFinancialEngine.parseAndValidateAccountingCsv(csvText, codes)
        _uiState.update {
            it.copy(
                accountingCsvPreviewRows = parsed,
                statusMessage = if (parsed.isEmpty()) null else "Parsed ${parsed.size} accounting rows (${parsed.count { r -> r.error == null }} valid).",
                errorMessage = if (parsed.isEmpty()) "CSV requires header + at least 1 balanced row." else null
            )
        }
    }

    fun clearAccountingCsvPreview() {
        _uiState.update { it.copy(accountingCsvPreviewRows = emptyList()) }
    }

    fun commitAccountingCsvImport() {
        val rows = _uiState.value.accountingCsvPreviewRows
        val names = _uiState.value.accounts.associate { it.code to it.name }
        runMutation("Accounting CSV rows atomically posted.") { org ->
            repository.commitAccountingCsvImport(org, currentUserId, userEmail, rows, names).also { res ->
                if (res.isSuccess) _uiState.update { it.copy(accountingCsvPreviewRows = emptyList()) }
            }
        }
    }

    fun reverseJournalEntry(entry: JournalEntryRecord, reversalDate: String) {
        runMutation("Journal entry reversed cleanly.") { org ->
            repository.reverseJournalEntry(org, currentUserId, userEmail, entry, reversalDate)
        }
    }

    fun saveCounterpartyItem(
        type: String,
        partyName: String,
        invoiceNo: String,
        currency: String,
        amount: String,
        dueDate: String
    ) {
        runMutation("AR/AP item recorded.") { org ->
            repository.saveCounterpartyItem(org, currentUserId, userEmail, type, partyName, invoiceNo, currency, amount, dueDate)
        }
    }

    fun recordCounterpartyPayment(item: CounterpartyRecord, paymentAmount: String) {
        runMutation("Payment applied to ${item.invoiceNo}.") { org ->
            repository.recordCounterpartyPayment(org, currentUserId, userEmail, item, paymentAmount)
        }
    }

    // --- Budgets ---

    fun saveBudget(
        name: String,
        type: String,
        period: String,
        currency: String,
        planned: String,
        actual: String,
        explanation: String,
        approveNow: Boolean
    ) {
        runMutation("Budget saved with variance calculation.") { org ->
            repository.saveBudget(org, currentUserId, userEmail, name, type, period, currency, planned, actual, explanation, approveNow)
        }
    }

    fun updateBudgetActuals(budget: BudgetRecord, actual: String, explanation: String, status: String) {
        runMutation("Budget actuals updated.") { org ->
            repository.updateBudgetActuals(org, currentUserId, userEmail, budget, actual, explanation, status)
        }
    }

    // --- Inventory & Stocktaking ---

    fun createInventoryItem(
        sku: String,
        name: String,
        itemType: String,
        unit: String,
        warehouse: String,
        valuationMethod: String,
        openingQty: String,
        openingUnitCost: String,
        marketPrice: String
    ) {
        runMutation("Inventory item created.") { org ->
            repository.createInventoryItem(
                org, currentUserId, userEmail, sku, name, itemType, unit,
                warehouse, valuationMethod, openingQty, openingUnitCost, marketPrice
            )
        }
    }

    fun recordInventoryMovement(
        item: InventoryItemRecord,
        movementType: String,
        qty: String,
        unitCost: String,
        warehouse: String,
        reason: String,
        referenceNo: String,
        date: String,
        postAccounting: Boolean
    ) {
        runMutation("Inventory movement ($movementType) recorded.") { org ->
            repository.recordInventoryMovement(org, currentUserId, userEmail, item, movementType, qty, unitCost, warehouse, reason, referenceNo, date, postAccounting)
        }
    }

    fun createStocktake(
        sessionName: String,
        warehouse: String,
        date: String,
        itemsInScope: List<InventoryItemRecord>,
        counts: Map<String, String>
    ) {
        runMutation("Stocktake submitted for review.") { org ->
            repository.createStocktakeSession(org, currentUserId, userEmail, sessionName, warehouse, date, itemsInScope, counts)
        }
    }

    fun processStocktakeDecision(session: StocktakeSessionRecord, decision: String) {
        val items = _uiState.value.inventoryItems
        runMutation("Stocktake marked $decision.") { org ->
            repository.processStocktakeDecision(org, currentUserId, userEmail, session, items, decision)
        }
    }

    fun submitScrapLoss(
        item: InventoryItemRecord,
        lossType: String,
        qty: String,
        reason: String,
        lossDate: String
    ) {
        runMutation("Scrap/loss request submitted for admin approval.") { org ->
            repository.submitScrapLossRequest(org, currentUserId, userEmail, item, lossType, qty, reason, lossDate)
        }
    }

    fun reviewScrapLoss(scrap: ScrapLossRecord, decision: String) {
        val item = _uiState.value.inventoryItems.firstOrNull { it.id == scrap.itemId }
        runMutation("Scrap loss request marked $decision.") { org ->
            repository.reviewScrapLossRequest(org, currentUserId, userEmail, scrap, item, decision)
        }
    }

    // --- AI Assistant (3 Modes) & Invoice OCR ---

    fun setAiMode(mode: String) {
        if (mode in setOf("general", "financial", "research")) {
            _uiState.update { it.copy(aiMode = mode) }
        }
    }

    fun askAiAssistant(question: String, languageCode: String) {
        if (question.isBlank()) return
        val mode = _uiState.value.aiMode
        val context = buildGroundedDataSummary()
        viewModelScope.launch {
            _uiState.update { it.copy(isAiStreaming = true, aiResponse = "", errorMessage = null) }
            var finalResponse = ""
            aiRepository.streamGroundedExplanation(question, context, languageCode, mode)
                .catch { e ->
                    _uiState.update { it.copy(isAiStreaming = false, errorMessage = e.message ?: "AI assistant failed") }
                }
                .collect { chunkText ->
                    finalResponse = chunkText
                    _uiState.update { it.copy(aiResponse = chunkText) }
                }
            _uiState.update {
                val updatedTranscript = if (finalResponse.isNotBlank()) {
                    it.chatTranscript + ("User [$mode]" to question) + ("FVG AI [$mode]" to finalResponse)
                } else it.chatTranscript
                it.copy(isAiStreaming = false, chatTranscript = updatedTranscript)
            }
        }
    }

    fun extractOcrInvoice(bitmap: Bitmap, fileName: String) {
        val org = _uiState.value.activeOrg ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isOcrExtracting = true, errorMessage = null) }
            try {
                val res = aiRepository.extractInvoiceDocument(bitmap)
                repository.saveOcrDraft(org, currentUserId, userEmail, fileName, res)
                    .onSuccess {
                        _uiState.update { it.copy(isOcrExtracting = false, statusMessage = "OCR draft extracted. Review carefully before confirming.") }
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(isOcrExtracting = false, errorMessage = e.message) }
                    }
            } catch (e: Exception) {
                _uiState.update { it.copy(isOcrExtracting = false, errorMessage = e.message ?: "OCR extraction failed") }
            }
        }
    }

    fun reviewOcrDraft(
        draft: OcrDraftRecord,
        vendorName: String,
        date: String,
        currency: String,
        amount: String,
        summary: String,
        status: String
    ) {
        runMutation("OCR draft marked $status.") { org ->
            repository.reviewOcrDraft(org, currentUserId, userEmail, draft, vendorName, date, currency, amount, summary, status)
        }
    }

    private fun buildGroundedDataSummary(): String {
        val st = _uiState.value
        val org = st.activeOrg
        val stmt = st.statementsReport
        val topProducts = st.products.take(12).joinToString("; ") {
            "${it.name} (${it.unit}, ${it.currency}): current=${it.currentPrice}, prev=${it.previousPrice}"
        }
        val topInflation = st.inflationRecords.take(8).joinToString("; ") {
            "${it.period} [${it.sourceType}]: monthly=${it.monthlyRatePct}%, index=${it.indexValue}"
        }
        val topCosting = st.costSnapshots.take(8).joinToString("; ") {
            "${it.productName} (${it.period}, ${it.costingMethod}): totalCost=${it.totalCost}, unitCost=${it.unitCost}, price=${it.sellingPrice}, margin=${it.profitMarginPct}%, risk=${it.riskLevel}"
        }
        val topInv = st.inventoryItems.take(10).joinToString("; ") {
            "${it.name} (${it.valuationMethod}): qty=${it.quantityOnHand} ${it.unit}, bookValue=${it.totalBookValue}, mktPrice=${it.currentMarketPrice}"
        }
        val ratioSummary = st.financialRatios.take(10).joinToString("; ") {
            "${it.key}=${it.value?.toPlainString() ?: "MISSING (${it.missingDataWarning})"}"
        }
        val researchSummary = st.researchRecords.take(10).joinToString("; ") {
            "[${it.recordType}/${it.dataStatus}] ${it.title}: ${it.summary.take(120)}"
        }
        return buildString {
            appendLine("Organization: ${org?.name ?: "None"} | Base Currency: ${org?.baseCurrency ?: "SDG"} | Plan: ${org?.planTier ?: "N/A"} | Status: ${org?.subscriptionStatus ?: "N/A"}")
            appendLine("Products (${st.products.size}): $topProducts")
            appendLine("Inflation (${st.inflationRecords.size}): $topInflation")
            appendLine("Cost Snapshots (${st.costSnapshots.size}): $topCosting")
            appendLine("Inventory (${st.inventoryItems.size}), Total Book=${st.totalInventoryBookValue}, Total Replacement=${st.totalReplacementCost}: $topInv")
            if (stmt != null) {
                appendLine("Financial Statements: Revenue=${stmt.revenue}, COGS=${stmt.cogs}, GrossProfit=${stmt.grossProfit}, OpEx=${stmt.operatingExpenses}, NetProfit=${stmt.netProfit}, Cash=${stmt.cashAndBank}, Assets=${stmt.totalAssets}, Liabilities=${stmt.totalLiabilities}, Equity=${stmt.totalEquityWithNetIncome}, Balanced=${stmt.isAccountingEquationBalanced}")
                appendLine("Ratios: $ratioSummary")
            } else {
                appendLine("Financial Statements: No accounts/statements initialized yet.")
            }
            if (st.researchRecords.isNotEmpty()) {
                appendLine("Academic Research Records (${st.researchRecords.size}): $researchSummary")
            }
        }
    }

    fun buildWorkspaceBackupJson(): String {
        val st = _uiState.value
        val orgName = st.activeOrg?.name ?: "FVG Workspace"
        val curr = st.activeOrg?.baseCurrency ?: "SDG"
        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        val counts = mapOf(
            "products" to st.products.size,
            "priceObservations" to st.priceObservations.size,
            "inflationRecords" to st.inflationRecords.size,
            "priceBaskets" to st.priceBaskets.size,
            "fxRates" to st.fxRates.size,
            "costSnapshots" to st.costSnapshots.size,
            "standardCosts" to st.standardCosts.size,
            "productionOrders" to st.productionOrders.size,
            "accounts" to st.accounts.size,
            "journalEntries" to st.journalEntries.size,
            "inventoryItems" to st.inventoryItems.size,
            "stocktakes" to st.stocktakes.size,
            "scrapLosses" to st.scrapLosses.size,
            "budgets" to st.budgets.size,
            "paymentRequests" to st.paymentRequests.size,
            "researchRecords" to st.researchRecords.size
        )
        val keys = listOf("statements", "trial_balance", "ratios", "prices", "inflation", "fx", "costing", "inventory", "stocktakes", "scrap", "budgets")
        return com.example.domain.export.FvgExportService.toWorkspaceBackupJson(
            organizationName = orgName,
            baseCurrency = curr,
            generatedAtIso = nowIso,
            sections = counts,
            payloads = keys.map { buildReportPayload(it) }
        )
    }

    fun buildReportPayload(reportType: String): ReportExportPayload {
        val st = _uiState.value
        val orgName = st.activeOrg?.name ?: "FVG Workspace"
        val curr = st.activeOrg?.baseCurrency ?: "SDG"
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        return when (reportType) {
            "prices" -> ReportExportPayload(
                title = "Prices & Percentage Change History",
                organizationName = orgName,
                currency = curr,
                generatedDate = today,
                methodNotes = "Formula: ((New - Old) / Old) × 100 | Records: ${st.priceObservations.size}",
                headers = listOf("Date", "Product", "Category", "Unit", "Currency", "Prev Price", "New Price", "% Change"),
                rows = st.priceObservations.map {
                    listOf(it.observationDate, it.productName, it.category, it.unit, it.currency, it.previousPrice, it.price, "${it.pctChange}%")
                },
                missingDataNotes = if (st.priceObservations.isEmpty()) listOf("No price observations recorded yet.") else emptyList()
            )
            "inflation" -> ReportExportPayload(
                title = "Inflation Indices & Weighted Baskets",
                organizationName = orgName,
                currency = curr,
                generatedDate = today,
                methodNotes = "Compounded Inflation Π(1 + r/100) - 1 | Baskets: ${st.priceBaskets.size}",
                headers = listOf("Period", "Category", "Monthly %", "Annual %", "Index", "Base Period", "Source"),
                rows = st.inflationRecords.map {
                    listOf(it.period, it.category, it.monthlyRatePct, it.annualRatePct, it.indexValue, it.basePeriod, "${it.sourceType}: ${it.sourceName}")
                },
                missingDataNotes = if (st.inflationRecords.isEmpty()) listOf("No inflation records stored yet.") else emptyList()
            )
            "fx" -> ReportExportPayload(
                title = "Historical Exchange Rates Ledger",
                organizationName = orgName,
                currency = curr,
                generatedDate = today,
                methodNotes = "Zero fabricated fallback rates; all rates sourced from explicit dated records.",
                headers = listOf("Effective Date", "Pair", "Rate", "Type", "Source"),
                rows = st.fxRates.map {
                    listOf(it.effectiveDate, "${it.baseCurrency}/${it.quoteCurrency}", it.rate, it.rateType, it.sourceName)
                },
                missingDataNotes = if (st.fxRates.isEmpty()) listOf("No FX rates recorded yet.") else emptyList()
            )
            "costing" -> ReportExportPayload(
                title = "Product Costing, Margin & Risk Summary",
                organizationName = orgName,
                currency = curr,
                generatedDate = today,
                methodNotes = "Engine v${FvgFinancialEngine.MODEL_VERSION} | 8 Methods | Margin=(Price-Cost)/Price, Markup=(Price-Cost)/Cost",
                headers = listOf("Period", "Product", "Method", "Total Cost", "Unit Cost", "Price", "Net Profit", "Margin %", "Risk"),
                rows = st.costSnapshots.map {
                    listOf(it.period, it.productName, it.costingMethod, it.totalCost, it.unitCost, it.sellingPrice, it.netProfit, "${it.profitMarginPct}%", it.riskLevel)
                },
                missingDataNotes = if (st.costSnapshots.isEmpty()) listOf("No cost calculation snapshots saved yet.") else emptyList()
            )
            "inventory" -> ReportExportPayload(
                title = "Inventory Book vs Replacement Cost Report",
                organizationName = orgName,
                currency = curr,
                generatedDate = today,
                methodNotes = "Book Value (FIFO / Moving Weighted Average) vs Current Market Replacement Cost",
                headers = listOf("SKU", "Item", "Method", "Qty", "Unit", "Book Value", "Market Unit", "Replacement Est."),
                rows = st.inventoryItems.map { item ->
                    val qty = FvgFinancialEngine.dOrZero(item.quantityOnHand)
                    val mkt = FvgFinancialEngine.dOrZero(item.currentMarketPrice)
                    val rep = if (mkt > BigDecimal.ZERO) FvgFinancialEngine.money(qty.multiply(mkt)).toPlainString() else "N/A"
                    listOf(item.sku, item.name, item.valuationMethod, item.quantityOnHand, item.unit, item.totalBookValue, item.currentMarketPrice, rep)
                },
                missingDataNotes = if (st.inventoryItems.isEmpty()) listOf("No inventory items registered yet.") else emptyList()
            )
            "stocktakes" -> ReportExportPayload(
                title = "Physical Stocktake Variance Report",
                organizationName = orgName,
                currency = curr,
                generatedDate = today,
                methodNotes = "Draft -> Review -> Approved & Posted Variance Workflow",
                headers = listOf("Date", "Session", "Warehouse", "Status", "Discrepancy Value"),
                rows = st.stocktakes.map {
                    listOf(it.sessionDate, it.sessionName, it.warehouse, it.status, it.totalDiscrepancyValue)
                },
                missingDataNotes = if (st.stocktakes.isEmpty()) listOf("No stocktake sessions recorded yet.") else emptyList()
            )
            "scrap" -> ReportExportPayload(
                title = "Inventory Scrap, Damaged & Expired Losses",
                organizationName = orgName,
                currency = curr,
                generatedDate = today,
                methodNotes = "Admin-approved inventory write-off linked to 6210 Inventory Loss & Scrap.",
                headers = listOf("Date", "Item", "Type", "Qty", "Total Loss", "Status", "Reason"),
                rows = st.scrapLosses.map {
                    listOf(it.lossDate, it.itemName, it.lossType, it.quantity, it.totalLossCost, it.status, it.reason)
                },
                missingDataNotes = if (st.scrapLosses.isEmpty()) listOf("No scrap/loss requests logged yet.") else emptyList()
            )
            "budgets" -> ReportExportPayload(
                title = "Budgets & Planned-vs-Actual Variance",
                organizationName = orgName,
                currency = curr,
                generatedDate = today,
                methodNotes = "Variance = Actual - Planned | Percentage = (Variance / Planned) × 100",
                headers = listOf("Period", "Budget", "Type", "Planned", "Actual", "Variance", "Var %", "Status"),
                rows = st.budgets.map {
                    listOf(it.period, it.name, it.budgetType, it.plannedAmount, it.actualAmount, it.variance, "${it.variancePct}%", it.status)
                },
                missingDataNotes = if (st.budgets.isEmpty()) listOf("No budgets saved yet.") else emptyList()
            )
            "trial_balance" -> {
                val stmt = st.statementsReport
                ReportExportPayload(
                    title = "Trial Balance Report",
                    organizationName = orgName,
                    currency = curr,
                    generatedDate = today,
                    methodNotes = "Debits: ${stmt?.totalTrialDebits ?: "0.00"} = Credits: ${stmt?.totalTrialCredits ?: "0.00"}",
                    headers = listOf("Code", "Account Name", "Type", "Debit Balance", "Credit Balance"),
                    rows = stmt?.trialBalance?.map {
                        listOf(it.code, it.name, it.accountType, it.debitBalance.toPlainString(), it.creditBalance.toPlainString())
                    } ?: emptyList(),
                    missingDataNotes = if (stmt == null) listOf("Chart of accounts not initialized.") else emptyList()
                )
            }
            "ledger" -> {
                val posted = st.journalEntries.filter { it.status == "posted" || it.status == "reversed" }
                ReportExportPayload(
                    title = "General Ledger Movements Summary",
                    organizationName = orgName,
                    currency = curr,
                    generatedDate = today,
                    methodNotes = "Chronological posted & reversed double-entry ledger lines (${posted.size} entries)",
                    headers = listOf("Date", "Ref No", "Status", "Description", "Debit", "Credit"),
                    rows = posted.map {
                        listOf(it.entryDate, it.referenceNo, it.status, it.description, it.totalDebit, it.totalCredit)
                    },
                    missingDataNotes = if (posted.isEmpty()) listOf("No posted journal entries yet.") else emptyList()
                )
            }
            "ratios" -> ReportExportPayload(
                title = "Financial Ratios Analysis Report",
                organizationName = orgName,
                currency = curr,
                generatedDate = today,
                methodNotes = "18 standardized liquidity, profitability, efficiency & leverage ratios with denominator checks.",
                headers = listOf("Category", "Ratio Key", "Value", "Formula", "Note / Warning"),
                rows = st.financialRatios.map {
                    listOf(it.category, it.key, it.value?.let { v -> "${v.toPlainString()} ${it.unitSuffix}" } ?: "N/A", it.formula, it.missingDataWarning ?: it.explanation)
                },
                missingDataNotes = st.financialRatios.mapNotNull { r -> r.missingDataWarning?.let { "${r.key}: $it" } }
            )
            else -> {
                val stmt = st.statementsReport
                val rows = if (stmt != null) {
                    listOf(
                        listOf("Income Statement", "Operating Revenue", stmt.revenue.toPlainString(), curr),
                        listOf("Income Statement", "Cost of Goods Sold", stmt.cogs.toPlainString(), curr),
                        listOf("Income Statement", "Gross Profit", stmt.grossProfit.toPlainString(), curr),
                        listOf("Income Statement", "Operating Expenses", stmt.operatingExpenses.toPlainString(), curr),
                        listOf("Income Statement", "Net Profit", stmt.netProfit.toPlainString(), curr),
                        listOf("Balance Sheet", "Cash & Bank", stmt.cashAndBank.toPlainString(), curr),
                        listOf("Balance Sheet", "Accounts Receivable", stmt.receivables.toPlainString(), curr),
                        listOf("Balance Sheet", "Inventory Book Value", stmt.inventoryBookValue.toPlainString(), curr),
                        listOf("Balance Sheet", "Total Assets", stmt.totalAssets.toPlainString(), curr),
                        listOf("Balance Sheet", "Total Liabilities", stmt.totalLiabilities.toPlainString(), curr),
                        listOf("Balance Sheet", "Total Equity (incl. Net Income)", stmt.totalEquityWithNetIncome.toPlainString(), curr),
                        listOf("Cash Flow", "Operating Cash Flow", stmt.operatingCashFlow.toPlainString(), curr),
                        listOf("Cash Flow", "Closing Cash", stmt.closingCash.toPlainString(), curr)
                    )
                } else emptyList()
                val missing = buildList {
                    if (stmt == null) add("Chart of accounts not initialized.")
                    st.financialRatios.filter { it.missingDataWarning != null }.take(5).forEach {
                        add("${it.key}: ${it.missingDataWarning}")
                    }
                }
                ReportExportPayload(
                    title = "Financial Statements & Position Report",
                    organizationName = orgName,
                    currency = curr,
                    generatedDate = today,
                    methodNotes = "Double-Entry Accrual Ledger | Equation Balanced: ${stmt?.isAccountingEquationBalanced ?: false}",
                    headers = listOf("Statement", "Line Item", "Amount", "Currency"),
                    rows = rows,
                    missingDataNotes = missing
                )
            }
        }
    }

    class Factory(
        private val repository: FvgRepository,
        private val aiRepository: FvgAiRepository,
        private val currentUserId: String,
        private val userEmail: String,
        private val userDisplayName: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FvgViewModel(repository, aiRepository, currentUserId, userEmail, userDisplayName) as T
        }
    }
}
