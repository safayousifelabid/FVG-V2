package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.*
import com.example.ui.i18n.fvgStrings
import com.example.ui.theme.*
import kotlinx.coroutines.delay

const val APPROVED_ARABIC_ABOUT_US =
    "في ظل متغيّرات اقتصادية متسارعة وضغوط تضخمية حادة تهدد استمرارية المشاريع، يأتي 'دليل رؤيتك المالي' ليكون بوصلتك نحو الاستقرار المالي. نحن لا نكتفي بحساب الأرقام، بل نسهل لك اتخاذ قرارات التسعير الصحيحة بشكل تلقائي ومدروس، لنحول معادلات التكاليف المعقدة إلى أدوات عملية دقيقة تضمن لك الحفاظ على رأس المال، تعظيم الأرباح، والعبور بمشروعك نحو بر الأمان بكل ثقة واحترافية."

@Composable
fun FvgSplashScreen(
    isArabic: Boolean,
    onSplashFinished: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(2000L)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FvgWarmIvory)
            .clickable { onSplashFinished() }
            .testTag("fvg_splash_screen")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Subtle diagonal #517D77 rays radiating from top-right
            val ray1 = Path().apply {
                moveTo(w * 0.56f, 0f)
                lineTo(w * 0.80f, 0f)
                lineTo(w * 0.34f, h * 0.44f)
                lineTo(w * 0.16f, h * 0.44f)
                close()
            }
            drawPath(ray1, color = FvgSoftTeal.copy(alpha = 0.08f))

            val ray2 = Path().apply {
                moveTo(w * 0.84f, 0f)
                lineTo(w, 0f)
                lineTo(w, h * 0.14f)
                lineTo(w * 0.52f, h * 0.50f)
                lineTo(w * 0.38f, h * 0.50f)
                close()
            }
            drawPath(ray2, color = FvgSoftTeal.copy(alpha = 0.06f))

            // Faint upward candlestick silhouettes in top-right corner
            val candles = listOf(
                Triple(0.75f, 0.14f, 0.055f),
                Triple(0.81f, 0.11f, 0.065f),
                Triple(0.87f, 0.085f, 0.075f),
                Triple(0.93f, 0.06f, 0.085f)
            )
            candles.forEachIndexed { idx, (xFrac, topFrac, bodyHFrac) ->
                val cx = w * xFrac
                val bodyW = w * 0.024f
                val bodyTop = h * topFrac
                val bodyH = h * bodyHFrac
                val wickColor = if (idx == candles.lastIndex) FvgWarmGold.copy(alpha = 0.22f) else FvgSoftTeal.copy(alpha = 0.14f)
                drawLine(
                    color = wickColor,
                    start = Offset(cx, bodyTop - h * 0.02f),
                    end = Offset(cx, bodyTop + bodyH + h * 0.02f),
                    strokeWidth = 3f
                )
                drawRoundRect(
                    color = wickColor,
                    topLeft = Offset(cx - bodyW / 2f, bodyTop),
                    size = Size(bodyW, bodyH),
                    cornerRadius = CornerRadius(4f, 4f)
                )
            }

            // Bottom 3-layer organic wave occupying strictly the bottom ~23% (h * 0.77f .. h)
            val forestCrest = Path().apply {
                moveTo(0f, h * 0.79f)
                cubicTo(w * 0.28f, h * 0.73f, w * 0.68f, h * 0.86f, w, h * 0.77f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(forestCrest, color = FvgForestGreen)

            val goldRibbon = Path().apply {
                moveTo(0f, h * 0.835f)
                cubicTo(w * 0.30f, h * 0.78f, w * 0.70f, h * 0.90f, w, h * 0.815f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(goldRibbon, color = FvgWarmGold)

            val deepGreenBase = Path().apply {
                moveTo(0f, h * 0.855f)
                cubicTo(w * 0.30f, h * 0.80f, w * 0.70f, h * 0.92f, w, h * 0.835f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(deepGreenBase, color = FvgDeepGreen)
        }

        // Logo + typography positioned higher in the open upper-middle warm ivory region
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            val topPad = (maxHeight * 0.11f).coerceAtLeast(36.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPad, start = 28.dp, end = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.fvg_brand_symbol),
                    contentDescription = "FVG Financial Vision Guide Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(134.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "FVG",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = FvgDeepGreen
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Financial Vision Guide",
                    fontFamily = MontserratFontFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = FvgDeepGreen,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.width(34.dp), thickness = 1.5.dp, color = FvgWarmGold)
                    Text(
                        text = "دليل رؤيتك المالي",
                        fontFamily = CairoFontFamily,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FvgForestGreen
                    )
                    HorizontalDivider(modifier = Modifier.width(34.dp), thickness = 1.5.dp, color = FvgWarmGold)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = if (isArabic) "قرارات أذكى.\nمستقبل مالي أقوى." else "Smarter Decisions.\nStronger Financial Future.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = FvgMutedForest,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }

            // Loading indicator & tap prompt cleanly anchored inside the bottom Deep Green wave
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CircularProgressIndicator(
                    color = FvgWarmGold,
                    trackColor = FvgWarmIvory.copy(alpha = 0.25f),
                    strokeWidth = 2.8.dp,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = if (isArabic) "انقر للمتابعة" else "Tap anywhere to continue",
                    style = MaterialTheme.typography.labelSmall,
                    color = FvgWarmIvory.copy(alpha = 0.88f)
                )
            }
        }
    }
}

@Composable
fun DeveloperShowcaseScreen(
    isArabic: Boolean,
    isDark: Boolean,
    onToggleLanguage: () -> Unit,
    onToggleTheme: () -> Unit,
    onContinue: () -> Unit
) {
    val s = fvgStrings(isArabic)
    BackHandler { onContinue() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(modifier = Modifier.statusBarsPadding()) {
                FvgTopHeaderBanner(
                    title = if (isArabic) "الباحثة ومطوّرة FVG" else "Researcher & FVG Vision",
                    subtitle = "Financial Vision Guide • دليل رؤيتك المالي",
                    icon = Icons.Filled.VerifiedUser,
                    isArabic = isArabic,
                    isDark = isDark,
                    onToggleLanguage = onToggleLanguage,
                    onToggleTheme = onToggleTheme
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("developer_showcase_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = FvgDeepGreen,
                border = BorderStroke(1.5.dp, FvgWarmGold),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        border = BorderStroke(3.dp, FvgWarmGold),
                        shadowElevation = 6.dp,
                        color = FvgSoftCream,
                        modifier = Modifier.size(164.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.zahra_yousif_portrait),
                            contentDescription = "Zahra Yousif Elabid Wagiealla Ahmed Portrait",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Text(
                        text = "Zahra Yousif Elabid Wagiealla Ahmed",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = FvgWarmIvory,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "زهراء يوسف العبد وقيع الله أحمد",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = FvgWarmGold,
                        textAlign = TextAlign.Center
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = FvgWarmGold.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, FvgWarmGold.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = if (isArabic)
                                "جامعة النيل — كلية إدارة الأعمال — تخصص المحاسبة — 2026"
                            else
                                "Nile University — Faculty of Business Administration — Accounting — 2026",
                            style = MaterialTheme.typography.labelLarge,
                            color = FvgWarmIvory,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                    Text(
                        text = "${s.contactEmail}  •  ${s.contactPhone}",
                        style = MaterialTheme.typography.labelSmall,
                        color = FvgWarmGold.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            FvgCard(containerColor = FvgSoftCream) {
                Text(
                    text = "رؤية دليل رؤيتك المالي (FVG)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FvgDeepGreen
                )
                Text(
                    text = APPROVED_ARABIC_ABOUT_US,
                    style = MaterialTheme.typography.bodyLarge,
                    color = FvgInkDark
                )
                HorizontalDivider(color = FvgWarmGold.copy(alpha = 0.5f))
                Text(
                    text = "Amid rapid economic shifts and acute inflationary pressures threatening business continuity, Financial Vision Guide (FVG) serves as your compass toward financial stability. We go beyond simple number-crunching to automate disciplined pricing decisions—turning complex cost equations into precise, practical tools that safeguard capital, maximize profit, and steer your enterprise safely forward with confidence.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FvgMutedForest
                )
            }

            FvgTrialCountdownBanner(
                trialEndsAtMillis = null,
                isArabic = isArabic,
                isPreview72h = true
            )

            FvgGoldButton(
                text = if (isArabic) "الدخول إلى منصة دليل رؤيتك المالي" else "Continue to Financial Vision Guide",
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                testTag = "developer_continue_button"
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun PublicWebsiteAndAuthScreen(
    isArabic: Boolean,
    isDark: Boolean,
    isSigningIn: Boolean,
    authError: String?,
    onToggleLanguage: () -> Unit,
    onToggleTheme: () -> Unit,
    onGoogleSignInClick: () -> Unit
) {
    val s = fvgStrings(isArabic)
    val context = LocalContext.current
    var selectedPage by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()
    LaunchedEffect(selectedPage) {
        scrollState.scrollTo(0)
    }
    val pageTitles = listOf(
        s.publicHome, s.publicFeatures, s.publicPricing,
        s.publicAbout, s.publicFaq, s.publicContact,
        s.publicPrivacy, s.publicTerms
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                FvgTopHeaderBanner(
                    title = "${s.brandShort} • ${s.brandTaglineEn}",
                    subtitle = s.brandTaglineAr,
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    isArabic = isArabic,
                    isDark = isDark,
                    onToggleLanguage = onToggleLanguage,
                    onToggleTheme = onToggleTheme
                )
                FvgSubTabRow(
                    tabs = pageTitles,
                    selectedIndex = selectedPage,
                    onSelect = { selectedPage = it },
                    testTagPrefix = "public_nav"
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!authError.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = authError,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            when (selectedPage) {
                0 -> PublicHomeSection(isArabic, isSigningIn, onGoogleSignInClick) { selectedPage = it }
                1 -> PublicFeaturesAndSectorsSection(isArabic)
                2 -> PublicPricingSection(isArabic, isSigningIn, onGoogleSignInClick)
                3 -> PublicAboutSection(isArabic)
                4 -> PublicGuideAndFaqSection(isArabic)
                5 -> PublicContactSection(isArabic) { subject, body ->
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:Financialvisionguide@gmail.com")
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                        putExtra(Intent.EXTRA_TEXT, body)
                    }
                    runCatching { context.startActivity(intent) }
                }
                6 -> PublicPrivacySection(isArabic)
                7 -> PublicTermsSection(isArabic)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PublicHomeSection(
    isArabic: Boolean,
    isSigningIn: Boolean,
    onSignInClick: () -> Unit,
    onNavigatePage: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = FvgDeepGreen,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.fvg_brand_symbol),
                    contentDescription = "FVG Brand Symbol",
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(FvgWarmIvory)
                        .padding(6.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isArabic) "منصة FVG للمحاسبة الإدارية والتسعير" else "FVG Management Accounting & Pricing",
                        style = MaterialTheme.typography.headlineMedium,
                        color = FvgWarmIvory
                    )
                    Text(
                        text = if (isArabic) "دليل رؤيتك المالي • قرارات أذكى ومستقبل مالي أقوى" else "Financial Vision Guide • Smarter Decisions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FvgWarmGold
                    )
                }
            }
            Text(
                text = if (isArabic)
                    "مراقبة تغيرات الأسعار والتضخم، 8 طرق لحساب تكاليف المنتجات وتحليل الانحرافات والمخاطر، القيود المحاسبية المزدوجة والقوائم المالية، تقييم المخزون بـ FIFO والمتوسط المرجح وتكلفة الاستبدال، ومساعد ذكاء اصطناعي بـ 3 أوضاع."
                else
                    "Track prices & inflation indices, compute 8 costing methods + risk levels + detailed variances, post double-entry journals & IFRS-aligned statements, value inventory (FIFO, MWA & replacement cost), manage Nobo thesis research, and query a 3-mode grounded AI assistant.",
                style = MaterialTheme.typography.bodyLarge,
                color = FvgWarmIvory.copy(alpha = 0.92f)
            )
            FvgGoldButton(
                text = if (isSigningIn) (if (isArabic) "جاري المصادقة..." else "Signing in...")
                else if (isArabic) "تسجيل الدخول / بدء تجربة 3 أيام عبر Google" else "Sign in / Start 3-Day Trial with Google",
                onClick = onSignInClick,
                enabled = !isSigningIn,
                icon = Icons.Filled.AccountCircle,
                modifier = Modifier.fillMaxWidth(),
                testTag = "google_sign_in_button"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onNavigatePage(1) },
                    border = BorderStroke(1.dp, FvgWarmGold),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text(if (isArabic) "المزايا والقطاعات" else "Features & Sectors", color = FvgWarmGold)
                }
                OutlinedButton(
                    onClick = { onNavigatePage(2) },
                    border = BorderStroke(1.dp, FvgWarmIvory.copy(alpha = 0.6f)),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text(if (isArabic) "الباقات وبنكك" else "Plans & Bankak", color = FvgWarmIvory)
                }
            }
        }
    }

    FvgTrialCountdownBanner(
        trialEndsAtMillis = null,
        isArabic = isArabic,
        isPreview72h = true,
        onActionClick = { onNavigatePage(2) }
    )

    // Founder & Researcher Card
    FvgCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.zahra_yousif_portrait),
                contentDescription = "Zahra Yousif Elabid Wagiealla Ahmed Portrait",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isArabic) "زهراء يوسف العبد وقيع الله أحمد" else "Zahra Yousif Elabid Wagiealla Ahmed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FvgDeepGreen
                )
                Text(
                    text = if (isArabic)
                        "جامعة النيل • كلية إدارة الأعمال • محاسبة (2026) — صاحبة منصة دليل رؤيتك المالي."
                    else
                        "Nile University • Faculty of Business Administration • Accounting (2026) — Owner & Researcher.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Isolated Commercial Case Card (Ben Al-Zahra)
    FvgCard {
        Text(
            text = if (isArabic) "عرض المشاريع التجارية — بن الزهراء (معزول عن بحث نوبو)" else "Commercial Reference — Ben Al-Zahra (Isolated Showcase)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = FvgDeepGreen
        )
        Image(
            painter = painterResource(id = R.drawable.fvg_ben_alzahra_products),
            contentDescription = "Ben Al-Zahra Commercial Products Showcase",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(16.dp))
        )
        Text(
            text = if (isArabic)
                "مثال مرئي لتطبيقات تسعير المنتجات التجارية ومراقبة تكاليف التعبئة والمواد الخام (بدون أي أرقام مخترعة، ومنفصل تماماً عن دراسة مصنع نوبو)."
            else
                "Visual commercial reference demonstrating retail packaging & raw-material costing inside FVG (zero fabricated ledger figures, strictly separated from the Nobo Factory study).",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // Academic Research Summary Card (Nobo)
    FvgCard(containerColor = FvgSoftCream) {
        Text(
            text = if (isArabic) "الإطار البحثي الأكاديمي — دراسة مصنع نوبو" else "Academic Field Research — Nobo Factory Study",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = FvgDeepGreen
        )
        Text(
            text = if (isArabic)
                "دور أساليب المحاسبة الإدارية في تسعير المنتجات الصناعية في بيئة تضخمية: دراسة ميدانية لمصنع نوبو في الخرطوم بحري، السودان.\nالإشراف الأكاديمي: د. فضل الله برير قفة إبراهيم."
            else
                "The Role of Management Accounting Techniques in Pricing Industrial Products in an Inflationary Environment: A Field Study of Nobo Factory in Khartoum North, Sudan.\nAcademic Supervisor: Dr. Fadl Allah Berier Gafa Ibrahim.",
            style = MaterialTheme.typography.bodyMedium,
            color = FvgInkDark
        )
    }
}

@Composable
private fun PublicFeaturesAndSectorsSection(isArabic: Boolean) {
    val s = fvgStrings(isArabic)
    val modules = if (isArabic) listOf(
        "مراقبة الأسعار والوحدات والتنبيهات" to "تسجيل تاريخي غير قابل للكتابة الفوقية، تنبيهات القفزات (>=15%)، منع التكرار، الرسوم البيانية، واستيراد CSV/XLSX.",
        "التضخم والسلات المرجحة والصرف التاريخي" to "مؤشرات شهرية وسنوية وتراكمية مع توضيح المصدر (رسمي/محسوب/يدوي/مبدئي) وتوثيق أسعار الصرف التاريخية.",
        "طرق التكاليف الـ 8 ومحرك المخاطر والانحراف" to "Actual, Standard, Target, ABC, Job Order, Process, Absorption, Variable مع تقييم المخاطر وتحليل انحراف السعر/الكمية والمعدل/الكفاءة.",
        "المحاسبة والقوائم المالية والأستاذ والنسب الـ 18" to "مسودة وترحيل وعكس القيود، دفتر الأستاذ العام بالرصيد المتحرك، مقارنة فترتين، أعمار الديون، و18 نسبة مالية.",
        "المخزون والجرد الفعلي والهدر والموازنات" to "FIFO والمتوسط المرجح وتكلفة الاستبدال، طلبات الهدر بموافقة المشرف، الجرد الفعلي، والموازنات.",
        "الذكاء الاصطناعي (3 أوضاع) + قراءة الفواتير + التصدير" to "أوضاع العام والتحليل المالي والبحث العلمي + OCR للفواتير + تصدير PDF / XLSX / CSV / JSON."
    ) else listOf(
        "Price Registry, Anomaly Alerts & Trends" to "Append-only observation trail, duplicate prevention, >=15% unusual movement alerts, historical spline charts, and sanitized CSV/XLSX import.",
        "Inflation, Weighted Baskets & Historical FX" to "Official/calculated/manual/provisional tagging, cumulative compounded inflation, weighted baskets, and historical FX lookup.",
        "8 Costing Methods, Risk Engine & Variance" to "Actual, Standard, Target, ABC, Job Order, Process, Absorption & Variable costing plus deterministic Risk Rating, Material/Labor Price/Usage/Rate/Efficiency Variances & Spec Presets.",
        "Double-Entry Accounting, GL, Aging & 18 Ratios" to "Draft/Post/Reverse journals, CSV accounting import, Account GL running balance, Period A vs B profit bridge, AR/AP aging & 18 ratios.",
        "Inventory, Scrap Approvals, Stocktaking & Budgets" to "FIFO/MWA valuation, Book vs Replacement vs Inflation-Adjusted comparison, admin-approved Scrap/Losses, stocktakes & budgets.",
        "3-Mode Grounded AI, OCR & Exports" to "General, Financial Analysis, and Academic Research AI modes + PickVisualMedia OCR drafts + PDF/XLSX/CSV/JSON & Workspace JSON backup."
    )

    modules.forEach { (title, desc) ->
        FvgCard {
            Text(title, style = MaterialTheme.typography.titleMedium, color = FvgDeepGreen)
            Text(desc, style = MaterialTheme.typography.bodyMedium)
        }
    }

    // Active Sectors & Coming Soon Sectors Roadmap
    FvgCard(containerColor = FvgSoftCream) {
        Text(
            text = if (isArabic) "القطاعات المفعّلة وخارطة القطاعات القادمة" else "Supported Sectors & Expansion Roadmap",
            style = MaterialTheme.typography.titleMedium,
            color = FvgDeepGreen
        )
        val activeSectors = listOf(
            if (isArabic) "التصنيع والإنتاج الصناعي (خطوط الإنتاج والتكلفة المعيارية)" else "Manufacturing & Industrial Production (Standard, Process & Job Costing)",
            if (isArabic) "الأغذية والمشروبات والتعبئة" else "Food, Beverage & Retail Packaging"
        )
        activeSectors.forEach { sec ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("• $sec", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(8.dp), color = FvgPositive.copy(alpha = 0.14f)) {
                    Text(s.activeSectorBadge, style = MaterialTheme.typography.labelSmall, color = FvgPositive, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
        }
        HorizontalDivider(color = FvgWarmGold.copy(alpha = 0.4f))
        val comingSoonSectors = if (isArabic) listOf(
            "التجارة والتجزئة والجملة",
            "الصيدلة والرعاية الصحية",
            "الزراعة والثروة الحيوانية",
            "المقاولات والإنشاءات",
            "الخدمات اللوجستية والنقل",
            "الضيافة والمطاعم",
            "الخدمات والاستشارات المهنية",
            "التجارة الإلكترونية والتوريد الرقمي"
        ) else listOf(
            "Retail & Wholesale Trade",
            "Pharmacy & Healthcare",
            "Agriculture & Livestock",
            "Construction & Contracting",
            "Logistics & Transport",
            "Hospitality & Catering",
            "Professional Services & Consulting",
            "E-Commerce & Digital Supply"
        )
        comingSoonSectors.forEach { sec ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("• $sec", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(8.dp), color = FvgWarmGold.copy(alpha = 0.25f)) {
                    Text(s.comingSoonBadge, style = MaterialTheme.typography.labelSmall, color = FvgDeepGreen, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
        }
    }
}

@Composable
private fun PublicPricingSection(
    isArabic: Boolean,
    isSigningIn: Boolean,
    onSignInClick: () -> Unit
) {
    FvgTrialCountdownBanner(
        trialEndsAtMillis = null,
        isArabic = isArabic,
        isPreview72h = true
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = FvgSoftCream,
        border = BorderStroke(1.dp, FvgWarmGold),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = if (isArabic)
                "الشفافية في الدفع: تبدأ المنظمات بفترة تجربة كاملة لمدة 3 أيام (72 ساعة) متزامنة مع الخادم. التفعيل الفعلي للباقات داخل السودان يتم عبر إرسال إثبات الدفع اليدوي (بنكك / أوكاش / تحويل بنكي) لمراجعة المشرف. الدفع ببطاقات Visa/Mastercard يظهر كمحوّل جاهز دون تفعيل تحصيل آلي لعدم وجود بوابة تاجر مفعّلة."
            else
                "Transparent Payment Model: Workspaces begin with a strict 3-day (72-hour) server-synced free trial. Live subscriptions in Sudan operate via Manual Payment Proof Verification (Bankak, O-Cash, or Bank Transfer — pending_verification -> admin approved). External Visa/Mastercard card billing is an unconfigured Gateway Adapter Notice.",
            style = MaterialTheme.typography.bodyMedium,
            color = FvgDeepGreen,
            modifier = Modifier.padding(14.dp)
        )
    }

    val plans = listOf(
        Triple(if (isArabic) "التجربة (3 أيام / 72 ساعة)" else "3-Day (72h) Free Trial", "0 SDG", listOf(
            if (isArabic) "كافة وحدات الأسعار والتكاليف والمحاسبة والمخزون" else "Full Prices, 8 Costing Methods, Accounting & Stock",
            if (isArabic) "عداد وقت حقيقي متزامن مع الخادم" else "Strict 72h Server-Synced Countdown Timer"
        )),
        Triple(if (isArabic) "الباقة الاحترافية (شهرياً)" else "Professional Plan", "Monthly / SDG or USD", listOf(
            if (isArabic) "تفعيل عبر بنكك / أوكاش / تحويل بنكي مع مراجعة الإيصال" else "Activation via Bankak / O-Cash / Bank Transfer Proof Review",
            if (isArabic) "FIFO والمتوسط المرجح + المساعد الذكي (3 أوضاع) + XLSX/PDF" else "FIFO/MWA, 3-Mode AI, Invoice OCR & PDF/XLSX Exports"
        )),
        Triple(if (isArabic) "باقة المؤسسات والفرق" else "Enterprise Plan", "Custom / Multi-User", listOf(
            if (isArabic) "تعدد المستخدمين وصلاحيات RBAC وسجل التدقيق" else "Multi-User RBAC Roles & Immutable Audit Trail",
            if (isArabic) "مساحة البحث الأكاديمي + سلات تضخم مخصصة" else "Nobo Research Workspace + Unlimited Baskets & FX History"
        ))
    )

    plans.forEach { (name, price, bullets) ->
        FvgCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = FvgDeepGreen)
                Surface(shape = RoundedCornerShape(12.dp), color = FvgWarmGold) {
                    Text(
                        price,
                        style = MaterialTheme.typography.labelLarge,
                        color = FvgDeepGreen,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            bullets.forEach { b ->
                Text("• $b", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(4.dp))
            FvgPrimaryButton(
                text = if (isArabic) "ابدأ تجربة 3 أيام عبر Google" else "Start 3-Day Trial with Google",
                onClick = onSignInClick,
                enabled = !isSigningIn
            )
        }
    }
}

@Composable
private fun PublicAboutSection(isArabic: Boolean) {
    FvgCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.zahra_yousif_portrait),
                contentDescription = "Zahra Yousif Elabid Wagiealla Ahmed Portrait",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isArabic) "زهراء يوسف العبد وقيع الله أحمد" else "Zahra Yousif Elabid Wagiealla Ahmed",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = FvgDeepGreen
                )
                Text(
                    text = if (isArabic)
                        "جامعة النيل — كلية إدارة الأعمال — تخصص المحاسبة — 2026"
                    else
                        "Nile University — Faculty of Business Administration — Accounting — 2026",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FvgAntiqueGold
                )
            }
        }
        Text(
            text = APPROVED_ARABIC_ABOUT_US,
            style = MaterialTheme.typography.bodyLarge,
            color = FvgInkDark
        )
        HorizontalDivider()
        Text(
            text = if (isArabic)
                "الإشراف الأكاديمي على بحث الماجستير/البكالوريوس لمصنع نوبو: د. فضل الله برير قفة إبراهيم.\nتجمع المنصة بين المحاسبة الإدارية الدقيقة والامتثال الكامل لعدم اختلاق أي بيانات ميدانية."
            else
                "Academic Supervisor for Nobo Factory Field Study: Dr. Fadl Allah Berier Gafa Ibrahim.\nFVG unifies deterministic management accounting, inflation indexing, and strict data integrity with zero fabricated figures.",
            style = MaterialTheme.typography.bodyMedium
        )
    }

    FvgCard {
        Text(
            text = if (isArabic) "المشروع التجاري المرجعي — بن الزهراء" else "Commercial Reference Case — Ben Al-Zahra",
            style = MaterialTheme.typography.titleMedium,
            color = FvgDeepGreen
        )
        Image(
            painter = painterResource(id = R.drawable.fvg_ben_alzahra_products),
            contentDescription = "Ben Al-Zahra Commercial Showcase",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(16.dp))
        )
    }
}

@Composable
private fun PublicGuideAndFaqSection(isArabic: Boolean) {
    FvgCard(containerColor = FvgSoftCream) {
        Text(
            text = if (isArabic) "دليل التشغيل السريع في 9 خطوات" else "9-Step Quick Start User Guide",
            style = MaterialTheme.typography.titleLarge,
            color = FvgDeepGreen
        )
        val steps = if (isArabic) listOf(
            "1. سجل الدخول بحساب Google لبدء تجربتك المجانية (3 أيام / 72 ساعة).",
            "2. أنشئ مساحة عمل المنظمة وحدد العملة الأساسية (الافتراضي SDG).",
            "3. أضف المنتجات والمواد الخام وسجل ملاحظات الأسعار الدورية.",
            "4. أدخل معدلات التضخم وسلة الأسعار المرجحة وأسعار الصرف التاريخية.",
            "5. احسب التكلفة بأحد الأساليب الـ 8 وراقب تقييم المخاطر ونقطة التعادل.",
            "6. سجّل التكلفة المعيارية وأوامر الإنتاج وحلل انحرافات السعر والكمية.",
            "7. رحّل القيود المزدوجة في المحاسبة وتابع القوائم المالية والـ 18 نسبة.",
            "8. قيّم المخزون بـ FIFO أو المتوسط المرجح وقارنه بتكلفة الاستبدال.",
            "9. استخدم الذكاء الاصطناعي بـ 3 أوضاع وصدّر تقارير PDF / XLSX / CSV / JSON."
        ) else listOf(
            "1. Sign in with Google to start your 3-day (72h) server-synced workspace trial.",
            "2. Initialize your Organization Workspace & base currency (SDG default).",
            "3. Register products/raw materials and record dated price observations.",
            "4. Track monthly/annual inflation rates, weighted baskets, and historical FX.",
            "5. Run any of the 8 Costing Methods and review Risk Rating & Break-Even CVP.",
            "6. Record Standard Cost Sheets & Production Orders to inspect Price/Usage variances.",
            "7. Post balanced double-entry journals to view Income, Balance Sheet & 18 Ratios.",
            "8. Manage FIFO/MWA inventory, replacement cost gaps, stocktakes & scrap approvals.",
            "9. Query 3-Mode AI, extract Invoice OCR drafts, or export PDF/XLSX/CSV/JSON."
        )
        steps.forEach { st ->
            Text(st, style = MaterialTheme.typography.bodyMedium, color = FvgInkDark)
        }
    }

    val faqs = if (isArabic) listOf(
        "ما مدة الفترة التجريبية وكيف يُحسب العداد؟" to
            "تستمر الفترة التجريبية 3 أيام كاملة (72 ساعة) بتوقيت الخادم، وتنتهي تلقائياً بعد 72 ساعة دون إمكانية تمديدها عند إنشاء منظمات إضافية.",
        "ما الفرق بين هامش الربح (Margin) ونسبة الإضافة (Markup)؟" to
            "الهامش يُحسب بقسمة الربح على سعر البيع، بينما Markup يُحسب بقسمة الربح على تكلفة الوحدة. تفصل حاسبة FVG بينهما بوضوح لمنع التسعير بأقل من المستهدف.",
        "كيف تتم مراجعة مدفوعات بنكك وأوكاش؟" to
            "يرفع المستخدم رقم المرجع وتاريخ التحويل واسم المرسل وتبقى الحالة 'pending_verification' حتى يعتمدها المشرف."
    ) else listOf(
        "How long is the free trial and how is it enforced?" to
            "Every user receives a strict 3-day (72-hour) free trial anchored to Firestore server time with a live monotonic D:H:M:S countdown.",
        "Why does FVG separate Profit Margin % from Markup %?" to
            "Margin divides unit profit by selling price, whereas Markup divides unit profit by unit cost. Mixing them causes systematic underpricing.",
        "How are Bankak & O-Cash payments verified?" to
            "Users submit the transfer reference, sender name, and date in Org & Billing; the request enters pending_verification until approved by an Org Admin."
    )

    faqs.forEach { (q, a) ->
        FvgCard {
            Text(q, style = MaterialTheme.typography.titleMedium, color = FvgDeepGreen)
            Text(a, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PublicContactSection(
    isArabic: Boolean,
    onSendEmail: (String, String) -> Unit
) {
    val s = fvgStrings(isArabic)
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }

    FvgCard {
        Text(
            text = if (isArabic) "تواصل مع دليل رؤيتك المالي (FVG)" else "Contact FVG — Financial Vision Guide",
            style = MaterialTheme.typography.titleLarge,
            color = FvgDeepGreen
        )
        Surface(shape = RoundedCornerShape(12.dp), color = FvgSoftCream, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Email: ${s.contactEmail}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = FvgDeepGreen)
                Text("Phone / WhatsApp: ${s.contactPhone}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = FvgForestGreen)
                Text(
                    text = if (isArabic)
                        "يتم فتح تطبيق البريد لإرسال رسالتك إلى ${s.contactEmail}."
                    else
                        "Email Adapter: Dispatches directly via your device email client to ${s.contactEmail}.",
                    style = MaterialTheme.typography.labelSmall,
                    color = FvgMutedForest
                )
            }
        }
        FvgField(name, { name = it }, if (isArabic) "الاسم الكامل" else "Full Name", testTag = "contact_name_input")
        FvgField(email, { email = it }, if (isArabic) "البريد الإلكتروني" else "Work Email", testTag = "contact_email_input")
        FvgField(topic, { topic = it }, if (isArabic) "الموضوع" else "Subject / Inquiry Topic")
        FvgField(message, { message = it }, if (isArabic) "تفاصيل الرسالة" else "Message Details", singleLine = false)

        if (feedback != null) {
            Text(feedback!!, style = MaterialTheme.typography.bodyMedium, color = FvgForestGreen)
        }

        FvgPrimaryButton(
            text = if (isArabic) "إرسال عبر البريد" else "Send Inquiry via Email App",
            icon = Icons.Filled.Email,
            onClick = {
                if (name.isBlank() || !email.contains("@") || message.isBlank()) {
                    feedback = if (isArabic) "يرجى إدخال الاسم وبريد صحيح ونص الرسالة." else "Please provide your name, a valid email, and your message."
                } else {
                    onSendEmail("FVG Inquiry: ${topic.ifBlank { name }}", "From: $name ($email)\n\n$message")
                    feedback = if (isArabic) "تم فتح تطبيق البريد لإرسال استفسارك." else "Prepared inquiry in your email client."
                }
            },
            testTag = "contact_submit_button"
        )
    }
}

@Composable
private fun PublicPrivacySection(isArabic: Boolean) {
    FvgCard {
        Text(
            text = if (isArabic) "سياسة الخصوصية وحماية البيانات" else "Privacy Policy & Tenant Isolation",
            style = MaterialTheme.typography.titleLarge,
            color = FvgDeepGreen
        )
        Text(
            text = if (isArabic)
                "• تخضع كافة بيانات المنظمات لعزل صارم متعدد المستأجرين (Multi-Tenant Isolation) عبر قواعد أمان Firestore ومفاتيح العضوية.\n• لا يتم مشاركة بيانات الأسعار أو الحسابات أو سجلات البحث مع أي منظمة أخرى.\n• المصادقة تتم حصراً عبر Google Identity و Firebase Auth."
            else
                "• Every organization's products, costs, journals, inventory, research records, and audit logs are strictly isolated inside its tenant boundary via zero-trust Firestore rules.\n• FVG never mixes external tenants or fabricates financial data.\n• Authentication uses Google Identity & Firebase Auth with Credential Manager and App Check protection.",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun PublicTermsSection(isArabic: Boolean) {
    FvgCard {
        Text(
            text = if (isArabic) "شروط الخدمة والمنهجية المحاسبية" else "Terms of Service & Accounting Methodology",
            style = MaterialTheme.typography.titleLarge,
            color = FvgDeepGreen
        )
        Text(
            text = if (isArabic)
                "• القيم الناتجة عن الذكاء الاصطناعي ومسودات الفواتير (OCR) تخضع للمراجعة البشرية قبل اعتمادها.\n• نتائج تحليل 'ماذا لو' تمثل سيناريوهات افتراضية وليست توقعات مستقبلية.\n• تبقى القيود المرحّلة غير قابلة للتعديل لضمان سلامة المراجعة المالية، ويتم التصحيح عبر قيود العكس."
            else
                "• AI analytical responses and document OCR extractions are decision-support drafts and require explicit human verification before posting.\n• What-If outputs are explicit deterministic simulations ('Scenario outcome — not a prediction').\n• Posted accounting journal entries are immutable to preserve audit integrity; corrections are executed via formal reversal entries.",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
