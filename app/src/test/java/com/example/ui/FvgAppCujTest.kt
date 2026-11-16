package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.data.model.CostSnapshot
import com.example.data.model.Organization
import com.example.data.model.UserProfile
import com.example.ui.components.FvgTrialCountdownBanner
import com.example.ui.components.FvgTrialExpiredGateCard
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DeveloperShowcaseScreen
import com.example.ui.screens.FvgSplashScreen
import com.example.ui.screens.PublicWebsiteAndAuthScreen
import com.example.ui.theme.FvgTheme
import com.example.ui.viewmodel.FvgUiState
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FvgAppCujTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `splash screen renders exact brand identity and responds to tap`() {
        var finished = false
        composeRule.setContent {
            FvgTheme(darkTheme = false, isArabic = false) {
                FvgSplashScreen(isArabic = false) { finished = true }
            }
        }
        composeRule.onNodeWithTag("fvg_splash_screen").assertIsDisplayed()
        composeRule.onNodeWithText("FVG").assertIsDisplayed()
        composeRule.onNodeWithText("Financial Vision Guide").assertIsDisplayed()
        composeRule.onNodeWithText("دليل رؤيتك المالي").assertIsDisplayed()
        composeRule.onNodeWithText("Smarter Decisions", substring = true).assertIsDisplayed()

        composeRule.onNodeWithTag("fvg_splash_screen").performClick()
        assertTrue(finished)
    }

    @Test
    fun `developer showcase renders canonical owner name and triggers continue`() {
        var continued = false
        composeRule.setContent {
            FvgTheme(darkTheme = false, isArabic = false) {
                DeveloperShowcaseScreen(
                    isArabic = false,
                    isDark = false,
                    onToggleLanguage = {},
                    onToggleTheme = {},
                    onContinue = { continued = true }
                )
            }
        }
        composeRule.onNodeWithText("Zahra Yousif Elabid Wagiealla Ahmed").assertIsDisplayed()
        composeRule.onNodeWithText("زهراء يوسف العبد وقيع الله أحمد").assertIsDisplayed()
        composeRule.onNodeWithTag("developer_continue_button").performScrollTo().performClick()
        assertTrue(continued)
    }

    @Test
    fun `public website displays coming soon sectors, manual Sudan payments, and contact details`() {
        composeRule.setContent {
            FvgTheme(darkTheme = false, isArabic = false) {
                PublicWebsiteAndAuthScreen(
                    isArabic = false,
                    isDark = false,
                    isSigningIn = false,
                    authError = null,
                    onToggleLanguage = {},
                    onToggleTheme = {},
                    onGoogleSignInClick = {}
                )
            }
        }
        composeRule.onNodeWithTag("google_sign_in_button").assertIsDisplayed()

        // Navigate to Features & Sectors tab (index 1)
        composeRule.onNodeWithTag("public_nav_tab_1").performScrollTo().performClick()
        composeRule.onNodeWithText("Retail & Wholesale Trade", substring = true).performScrollTo().assertIsDisplayed()

        // Navigate to Plans & Pay tab (index 2)
        composeRule.onNodeWithTag("public_nav_tab_2").performScrollTo().performClick()
        composeRule.onNodeWithText("Transparent Payment Model", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("pending_verification", substring = true).assertIsDisplayed()

        // Navigate to Contact tab (index 5)
        composeRule.onNodeWithTag("public_nav_tab_5").performScrollTo().performClick()
        composeRule.onNodeWithText("Email: Financialvisionguide@gmail.com", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Phone / WhatsApp: 00249911569966", substring = true).assertIsDisplayed()
    }

    @Test
    fun `trial expired gate renders lock card and routes upgrade button`() {
        var upgraded = false
        composeRule.setContent {
            FvgTheme(darkTheme = false, isArabic = false) {
                FvgTrialExpiredGateCard(
                    isArabic = false,
                    orgName = "Nobo Test Org",
                    onUpgradeClick = { upgraded = true },
                    onSignOutClick = {}
                )
            }
        }
        composeRule.onNodeWithTag("trial_expired_gate_card").assertIsDisplayed()
        composeRule.onNodeWithText("Bankak", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("trial_gate_upgrade_button").performClick()
        assertTrue(upgraded)
    }

    @Test
    fun `trial countdown banner formats active 72h server-synced timer cleanly`() {
        val now = 1_700_000_000_000L
        val ends = now + 72L * 3600_000L
        composeRule.setContent {
            FvgTheme(darkTheme = false, isArabic = false) {
                FvgTrialCountdownBanner(
                    trialEndsAtMillis = ends,
                    isArabic = false,
                    isPreview72h = false,
                    serverAnchorMillis = now,
                    elapsedBaselineMillis = android.os.SystemClock.elapsedRealtime()
                )
            }
        }
        composeRule.onNodeWithTag("trial_countdown_banner").assertIsDisplayed()
        composeRule.onNodeWithText("3-Day Active Trial", substring = true).assertIsDisplayed()
    }

    @Test
    fun `dashboard shows financial risk summary and navigates quick research shortcut`() {
        var navigatedTab = -1
        val now = System.currentTimeMillis()
        val org = Organization(
            id = "org_1",
            name = "Zahra Roastery",
            baseCurrency = "SDG",
            ownerId = "u_1",
            memberUids = listOf("u_1"),
            roles = mapOf("u_1" to "owner"),
            planTier = "trial",
            subscriptionStatus = "trial",
            trialDurationHours = 72L,
            trialEndsAt = Timestamp(Date(now + 48L * 3600_000L))
        )
        val snap = CostSnapshot(
            id = "cs_1",
            productName = "Ben Special 1kg",
            period = "2026-11",
            profitMarginPct = "8.50",
            riskLevel = "high"
        )
        val state = FvgUiState(
            isLoading = false,
            userProfile = UserProfile(userId = "u_1", email = "z@fvg.test", displayName = "Zahra"),
            organizations = listOf(org),
            activeOrg = org,
            costSnapshots = listOf(snap),
            serverAnchorMillis = now
        )
        composeRule.setContent {
            FvgTheme(darkTheme = false, isArabic = false) {
                DashboardScreen(
                    state = state,
                    userDisplayName = "Zahra",
                    isArabic = false,
                    isDark = false,
                    onToggleLanguage = {},
                    onToggleTheme = {},
                    onCreateOrganization = { _, _ -> },
                    onNavigateTab = { navigatedTab = it }
                )
            }
        }
        composeRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_screen").performScrollToNode(hasText("HIGH RISK"))
        composeRule.onNodeWithText("HIGH RISK").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_screen").performScrollToNode(hasTestTag("quick_research"))
        composeRule.onNodeWithTag("quick_research").performClick()
        assertEquals(5, navigatedTab)
    }
}
