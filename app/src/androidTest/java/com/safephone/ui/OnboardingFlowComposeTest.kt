package com.safephone.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

@RunWith(AndroidJUnit4::class)
class OnboardingFlowComposeTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    private val onboardingRule = object : TestWatcher() {
        override fun starting(description: Description) {
            // Do not block the main thread here — it breaks Activity/Nav lifecycle during launch.
            val c = InstrumentationRegistry.getInstrumentation().targetContext
            runBlocking(Dispatchers.IO) {
                FocusPreferences(c).setOnboardingCompleted(false)
            }
        }
    }

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(onboardingRule).around(composeRule)

    @After
    fun tearDown() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        FocusEnforcementService.stop(c)
    }

    @Test
    fun finish_onboarding_navigates_to_home() {
        composeRule.onNodeWithTag(SafePhoneTestTags.ONBOARDING_FINISH).performScrollTo()
        composeRule.onNodeWithTag(SafePhoneTestTags.ONBOARDING_FINISH).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SafePhoneTestTags.HOME_ENFORCEMENT_SWITCH).assertIsDisplayed()
    }
}
