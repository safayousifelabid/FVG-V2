package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.configureAppCheck
import com.example.data.repository.FvgAiRepository
import com.example.data.repository.FvgRepository
import com.example.ui.auth.AuthHelper
import com.example.ui.components.FvgTrialExpiredGateCard
import com.example.ui.i18n.fvgStrings
import com.example.ui.screens.*
import com.example.ui.theme.FvgDeepGreen
import com.example.ui.theme.FvgTheme
import com.example.ui.theme.FvgWarmGold
import com.example.ui.theme.FvgWarmIvory
import com.example.ui.viewmodel.FvgViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            FvgRootApp()
        }
    }
}

private data class FvgNavItem(
    val index: Int,
    val label: String,
    val icon: ImageVector,
    val tag: String
)

@Composable
fun FvgRootApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }

    var currentUser by remember { mutableStateOf<FirebaseUser?>(auth.currentUser) }
    var showSplash by remember { mutableStateOf(true) }
    var showDeveloperIntro by remember { mutableStateOf(true) }
    var isArabic by remember { mutableStateOf(false) }
    var isDark by remember { mutableStateOf(false) }
    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            AuthHelper.attemptSilentGoogleSignIn(context, auth)
        }
    }

    val repository = remember(context) { FvgRepository(context.applicationContext) }
    val aiRepository = remember { FvgAiRepository() }

    FvgTheme(darkTheme = isDark, isArabic = isArabic) {
        if (showSplash) {
            FvgSplashScreen(isArabic = isArabic) {
                showSplash = false
            }
        } else if (showDeveloperIntro) {
            BackHandler(enabled = currentUser != null) {
                showDeveloperIntro = false
            }
            DeveloperShowcaseScreen(
                isArabic = isArabic,
                isDark = isDark,
                onToggleLanguage = { isArabic = !isArabic },
                onToggleTheme = { isDark = !isDark },
                onContinue = { showDeveloperIntro = false }
            )
        } else if (currentUser == null) {
            PublicWebsiteAndAuthScreen(
                isArabic = isArabic,
                isDark = isDark,
                isSigningIn = isSigningIn,
                authError = authError,
                onToggleLanguage = { isArabic = !isArabic },
                onToggleTheme = { isDark = !isDark },
                onGoogleSignInClick = {
                    if (!isSigningIn) {
                        scope.launch {
                            isSigningIn = true
                            authError = null
                            AuthHelper.signInWithGoogleExplicit(context, auth)
                                .onFailure { e ->
                                    if (e !is AuthHelper.CancelledAuthException) {
                                        authError = e.message ?: "Google Sign-In failed"
                                    }
                                }
                            isSigningIn = false
                        }
                    }
                }
            )
        } else {
            val user = currentUser!!
            val vm: FvgViewModel = viewModel(
                key = user.uid,
                factory = FvgViewModel.Factory(
                    repository = repository,
                    aiRepository = aiRepository,
                    currentUserId = user.uid,
                    userEmail = user.email.orEmpty(),
                    userDisplayName = user.displayName ?: "FVG User"
                )
            )
            val uiState by vm.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(uiState.userProfile?.preferredLanguage) {
                uiState.userProfile?.preferredLanguage?.let { lang ->
                    isArabic = (lang == "ar")
                }
            }
            LaunchedEffect(uiState.userProfile?.darkMode) {
                uiState.userProfile?.darkMode?.let { dark ->
                    isDark = dark
                }
            }

            FvgAuthenticatedShell(
                vm = vm,
                uiState = uiState,
                userDisplayName = user.displayName ?: user.email ?: "FVG User",
                isArabic = isArabic,
                isDark = isDark,
                onToggleLanguage = {
                    val next = !isArabic
                    isArabic = next
                    vm.setLanguage(if (next) "ar" else "en")
                },
                onToggleTheme = {
                    val next = !isDark
                    isDark = next
                    vm.setDarkMode(next)
                },
                onOpenDeveloperIntro = { showDeveloperIntro = true },
                onSignOut = {
                    scope.launch {
                        AuthHelper.signOut(context, auth)
                    }
                }
            )
        }
    }
}

@Composable
private fun FvgAuthenticatedShell(
    vm: FvgViewModel,
    uiState: com.example.ui.viewmodel.FvgUiState,
    userDisplayName: String,
    isArabic: Boolean,
    isDark: Boolean,
    onToggleLanguage: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenDeveloperIntro: () -> Unit,
    onSignOut: () -> Unit
) {
    val s = fvgStrings(isArabic)
    var selectedTab by remember { mutableIntStateOf(0) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val notifications = remember(
        uiState.activeOrg,
        uiState.priceObservations,
        uiState.counterpartyItems,
        uiState.scrapLosses,
        uiState.stocktakes,
        uiState.ocrDrafts,
        uiState.journalEntries,
        uiState.statementsReport,
        uiState.paymentRequests
    ) { uiState.buildNotifications() }

    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    LaunchedEffect(uiState.statusMessage, uiState.errorMessage) {
        val msg = uiState.errorMessage ?: uiState.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            vm.clearMessages()
        }
    }

    val navItems = listOf(
        FvgNavItem(0, s.navDashboard, Icons.Filled.Dashboard, "nav_dashboard"),
        FvgNavItem(1, s.navPrices, Icons.AutoMirrored.Filled.TrendingUp, "nav_prices"),
        FvgNavItem(2, s.navCosting, Icons.Filled.Calculate, "nav_costing"),
        FvgNavItem(3, s.navAccounting, Icons.Filled.AccountBalance, "nav_accounting"),
        FvgNavItem(4, s.navInventory, Icons.Filled.Inventory2, "nav_inventory"),
        FvgNavItem(5, s.navResearch, Icons.Filled.School, "nav_research"),
        FvgNavItem(6, s.navReportsAi, Icons.Filled.Insights, "nav_reports")
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = FvgDeepGreen,
                        contentColor = FvgWarmIvory
                    ) {
                        navItems.forEach { item ->
                            val selected = selectedTab == item.index
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectedTab = item.index },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = FvgDeepGreen,
                                    selectedTextColor = FvgWarmGold,
                                    indicatorColor = FvgWarmGold,
                                    unselectedIconColor = FvgWarmIvory.copy(alpha = 0.72f),
                                    unselectedTextColor = FvgWarmIvory.copy(alpha = 0.72f)
                                ),
                                modifier = Modifier.testTag(item.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = FvgDeepGreen,
                        contentColor = FvgWarmIvory
                    ) {
                        navItems.forEach { item ->
                            val selected = selectedTab == item.index
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectedTab = item.index },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = FvgDeepGreen,
                                    selectedTextColor = FvgWarmGold,
                                    indicatorColor = FvgWarmGold,
                                    unselectedIconColor = FvgWarmIvory.copy(alpha = 0.72f),
                                    unselectedTextColor = FvgWarmIvory.copy(alpha = 0.72f)
                                ),
                                modifier = Modifier.testTag(item.tag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    val trialLocked = uiState.isTrialExpiredAndLocked()
                    if (trialLocked && selectedTab in 1..4) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            FvgTrialExpiredGateCard(
                                isArabic = isArabic,
                                orgName = uiState.activeOrg?.name ?: "FVG Workspace",
                                onUpgradeClick = { selectedTab = 6 },
                                onSignOutClick = onSignOut
                            )
                        }
                    } else {
                        when (selectedTab) {
                            0 -> DashboardScreen(
                                state = uiState,
                                userDisplayName = userDisplayName,
                                isArabic = isArabic,
                                isDark = isDark,
                                unreadNotificationsCount = notifications.size,
                                onNotificationsClick = { showNotificationsDialog = true },
                                onOpenDeveloperIntro = onOpenDeveloperIntro,
                                onToggleLanguage = onToggleLanguage,
                                onToggleTheme = onToggleTheme,
                                onCreateOrganization = { name, curr -> vm.createOrganization(name, curr) },
                                onNavigateTab = { selectedTab = it }
                            )
                            1 -> PricesInflationScreen(
                                state = uiState,
                                viewModel = vm,
                                isArabic = isArabic,
                                isDark = isDark,
                                onToggleLanguage = onToggleLanguage,
                                onToggleTheme = onToggleTheme
                            )
                            2 -> CostingScenariosScreen(
                                state = uiState,
                                viewModel = vm,
                                isArabic = isArabic,
                                isDark = isDark,
                                onToggleLanguage = onToggleLanguage,
                                onToggleTheme = onToggleTheme
                            )
                            3 -> AccountingStatementsScreen(
                                state = uiState,
                                viewModel = vm,
                                isArabic = isArabic,
                                isDark = isDark,
                                onToggleLanguage = onToggleLanguage,
                                onToggleTheme = onToggleTheme
                            )
                            4 -> InventoryBudgetsScreen(
                                state = uiState,
                                viewModel = vm,
                                isArabic = isArabic,
                                isDark = isDark,
                                onToggleLanguage = onToggleLanguage,
                                onToggleTheme = onToggleTheme
                            )
                            5 -> ResearchAndBenAlZahraScreen(
                                state = uiState,
                                viewModel = vm,
                                isArabic = isArabic,
                                isDark = isDark,
                                onToggleLanguage = onToggleLanguage,
                                onToggleTheme = onToggleTheme
                            )
                            else -> ReportsAiSettingsScreen(
                                state = uiState,
                                viewModel = vm,
                                isArabic = isArabic,
                                isDark = isDark,
                                onToggleLanguage = onToggleLanguage,
                                onToggleTheme = onToggleTheme,
                                onOpenDeveloperIntro = onOpenDeveloperIntro,
                                onSignOutClick = onSignOut
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNotificationsDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationsDialog = false },
            title = {
                Text(s.notificationsTitle, fontWeight = FontWeight.Bold)
            },
            text = {
                if (notifications.isEmpty()) {
                    Text(
                        text = if (isArabic) "لا توجد تنبيهات نشطة حالياً." else "All financial checks look clear — no active alerts.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(notifications, key = { it.id }) { alert ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedTab = alert.targetTab
                                        showNotificationsDialog = false
                                    }
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (isArabic) alert.titleAr else alert.titleEn,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (alert.severity == "critical") Color(0xFFB3261E) else FvgDeepGreen,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(alert.severity.uppercase(), style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text(
                                        text = if (isArabic) alert.detailAr else alert.detailEn,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotificationsDialog = false }) {
                    Text(if (isArabic) "إغلاق" else "Close")
                }
            }
        )
    }
}
