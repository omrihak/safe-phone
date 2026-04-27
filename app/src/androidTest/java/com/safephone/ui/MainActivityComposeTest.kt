package com.safephone.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.safephone.data.FocusPreferences
import com.safephone.service.FocusEnforcementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import androidx.test.platform.app.InstrumentationRegistry

@RunWith(AndroidJUnit4::class)
class MainActivityComposeTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    private val prefsRule = object : TestWatcher() {
        override fun starting(description: Description) {
            val c = InstrumentationRegistry.getInstrumentation().targetContext
            runBlocking(Dispatchers.IO) {
                FocusPreferences(c).setOnboardingCompleted(true)
            }
        }
    }

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(prefsRule).around(composeRule)

    @After
    fun tearDown() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        FocusEnforcementService.stop(c)
    }

    @Test
    fun home_shows_break_balance_and_start_break() {
        composeRule.onNodeWithTag(SafePhoneTestTags.HOME_BREAKS_BALANCE).assertIsDisplayed()
        composeRule.onNodeWithTag(SafePhoneTestTags.HOME_START_BREAK).assertIsDisplayed()
    }

    @Test
    fun navigate_blocked_add_shows_in_list() {
        composeRule.onNodeWithTag(SafePhoneTestTags.HOME_NAV_BLOCKED).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SafePhoneTestTags.BLOCKED_MANUAL_SECTION).performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SafePhoneTestTags.BLOCKED_PACKAGE_FIELD, useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag(SafePhoneTestTags.BLOCKED_PACKAGE_FIELD, useUnmergedTree = true).performTextInput("com.blocked.instr")
        composeRule.onNodeWithTag(SafePhoneTestTags.BLOCKED_ADD).performClick()
        composeRule.waitForIdle()
        // Field + list row both expose the same text; target the list row (last match).
        composeRule.onAllNodesWithText("com.blocked.instr", substring = true)[1].performScrollTo()
        composeRule.onAllNodesWithText("com.blocked.instr", substring = true)[1].assertIsDisplayed()
    }

    @Test
    fun navigate_domains_add_rule() {
        composeRule.onNodeWithTag(SafePhoneTestTags.HOME_NAV_DOMAINS).performClick()
        composeRule.onNodeWithTag(SafePhoneTestTags.DOMAIN_PATTERN_FIELD).performTextInput(".corp.test")
        composeRule.onNodeWithTag(SafePhoneTestTags.DOMAIN_ADD).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(".corp.test", substring = true)[1].performScrollTo()
        composeRule.onAllNodesWithText(".corp.test", substring = true)[1].assertIsDisplayed()
    }

    @Test
    fun navigate_breaks_save_shows_values() {
        composeRule.onNodeWithTag(SafePhoneTestTags.HOME_NAV_BREAKS).performClick()
        composeRule.onNodeWithTag(SafePhoneTestTags.BREAKS_MAX_FIELD).performTextInput("4")
        composeRule.onNodeWithTag(SafePhoneTestTags.BREAKS_DURATION_FIELD).performTextInput("12")
        composeRule.onNodeWithTag(SafePhoneTestTags.BREAKS_SAVE).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SafePhoneTestTags.BREAKS_MAX_FIELD).assertIsDisplayed()
    }

    @Test
    fun navigate_budgets_shows_search_field() {
        composeRule.onNodeWithTag(SafePhoneTestTags.HOME_NAV_BUDGETS).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SafePhoneTestTags.BUDGET_SEARCH_FIELD).assertIsDisplayed()
    }
}
