package com.familyquest.app.ui

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import com.familyquest.app.ui.theme.FamilyQuestTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FirstRunExperienceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun landingStartsAtFirstOnboardingStep() {
        setFirstRunContent()

        composeRule.onNodeWithTag("first-run-landing").assertExists()
        composeRule.onNodeWithTag("first-run-start").assertExists()
        val headlineLayouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag("first-run-headline")
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                action(headlineLayouts)
            }
        assertEquals(1, headlineLayouts.single().lineCount)

        composeRule.tapAndRender("first-run-start")

        composeRule.onNodeWithTag("first-run-landing").assertDoesNotExist()
        composeRule.onNodeWithTag("first-run-onboarding").assertExists()
        composeRule.onNodeWithTag("first-run-step-1").assertExists()
    }

    @Test
    fun nextTraversesAllFourStepsAndFinishCompletes() {
        var completed = false
        setFirstRunContent(onComplete = { completed = true })
        composeRule.tapAndRender("first-run-start")

        composeRule.onNodeWithTag("first-run-step-1").assertExists()
        composeRule.tapAndRender("first-run-next")
        composeRule.onNodeWithTag("first-run-step-2").assertExists()
        composeRule.tapAndRender("first-run-next")
        composeRule.onNodeWithTag("first-run-step-3").assertExists()
        composeRule.tapAndRender("first-run-next")
        composeRule.onNodeWithTag("first-run-step-4").assertExists()
        composeRule.onNodeWithTag("first-run-finish").assertExists()

        composeRule.tapAndRender("first-run-finish")

        composeRule.runOnIdle { assertTrue(completed) }
    }

    @Test
    fun skipCompletesFromOnboarding() {
        var completed = false
        setFirstRunContent(onComplete = { completed = true })
        composeRule.tapAndRender("first-run-start")

        composeRule.tapAndRender("first-run-skip")

        composeRule.runOnIdle { assertTrue(completed) }
    }

    @Test
    fun nonFinalSlidesAutoAdvanceAfterFourPointFiveSecondsButFinalSlideDoesNotComplete() {
        var completed = false
        setFirstRunContent(onComplete = { completed = true })
        composeRule.tapAndRender("first-run-start")

        composeRule.mainClock.advanceTimeBy(4_400)
        composeRule.onNodeWithTag("first-run-step-1").assertExists()
        composeRule.advanceAndRender(200)
        composeRule.onNodeWithTag("first-run-step-2").assertExists()

        composeRule.advanceAndRender(4_600)
        composeRule.onNodeWithTag("first-run-step-3").assertExists()
        composeRule.advanceAndRender(4_600)
        composeRule.onNodeWithTag("first-run-step-4").assertExists()

        composeRule.advanceAndRender(10_000)
        composeRule.onNodeWithTag("first-run-step-4").assertExists()
        composeRule.runOnIdle { assertFalse(completed) }
    }

    private fun setFirstRunContent(onComplete: () -> Unit = {}) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FamilyQuestTheme(darkTheme = true) {
                FirstRunExperience(onComplete = onComplete)
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }
}

private fun ComposeContentTestRule.tapAndRender(testTag: String) {
    onNodeWithTag(testTag).performClick()
    mainClock.advanceTimeByFrame()
    waitForIdle()
}

private fun ComposeContentTestRule.advanceAndRender(milliseconds: Long) {
    mainClock.advanceTimeBy(milliseconds)
    mainClock.advanceTimeByFrame()
    waitForIdle()
}
