package com.dnlfx.remindergenie

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.dnlfx.remindergenie.core.ItemDraft
import com.dnlfx.remindergenie.core.TrackedItem
import com.dnlfx.remindergenie.reminders.Reminders
import com.dnlfx.remindergenie.ui.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Drives the real app: adding, validation, swipe to replace and Undo, pause and snooze rules,
 * history rewinds, deleting, and a real reminder handled with "Replaced it".
 *
 * Each test starts from an empty list. They use the app's own storage, so run them on an
 * emulator or a test device, not a phone with items you care about.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @get:Rule val compose: ComposeTestRule = createEmptyComposeRule()

    @get:Rule val notifications: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val graph get() = context.graph
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun reset() {
        graph.store.all.forEach { graph.store.delete(it.id) }
        graph.settings.setAskedForNotifications()
        graph.settings.setDueSoonDays(3)
        context.getSystemService(NotificationManager::class.java).cancelAll()
    }

    @After fun close() {
        scenario?.close()
        graph.store.all.forEach { graph.store.delete(it.id) }
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    private fun daysAgo(days: Long): Instant = ZonedDateTime.now().minusDays(days).toInstant()

    private fun seed(name: String, startedDaysAgo: Long, everyDays: Int, spares: Int? = null): TrackedItem =
        graph.store.add(
            ItemDraft(
                name = name,
                symbolName = "eye",
                intervalValue = everyDays,
                startDate = daysAgo(startedDaysAgo),
                tracksSpares = spares != null,
                spareCount = spares ?: 0,
            ),
        )

    /** Waits for app state that isn't on screen, such as the store or posted notifications. */
    private fun poll(timeout: Long = 5_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeout
        while (!condition()) {
            if (System.currentTimeMillis() > end) throw AssertionError("Timed out waiting")
            Thread.sleep(50)
        }
    }

    private fun ComposeTestRule.waitFor(condition: () -> Boolean) = waitUntil(5_000, condition)

    private fun ComposeTestRule.waitForTag(tag: String) =
        waitFor { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeTestRule.waitForText(text: String, substring: Boolean = false) =
        waitFor { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    /** A deliberate drag across most of the row, like a finger, rather than an instant flick. */
    private fun swipeRowRight(tag: String) {
        compose.onNodeWithTag(tag).performTouchInput {
            swipeRight(startX = left + width * 0.05f, endX = right, durationMillis = 400)
        }
        compose.waitForIdle()
    }

    @Test fun addFromATemplateAndOpenIt() {
        launch()
        compose.onNodeWithText("Nothing to track yet").assertExists()
        compose.onNodeWithText("Add your first item").performClick()
        compose.onNodeWithTag("template-Toothbrush").performScrollTo().performClick()
        compose.onNodeWithText("New item").assertExists()
        compose.onNodeWithTag("save").assertIsEnabled().performClick()

        compose.waitForTag("item-Toothbrush")
        assertEquals(listOf("Toothbrush"), graph.store.all.map { it.name })
        compose.onNodeWithTag("item-Toothbrush").performClick()
        compose.onNodeWithTag("countdown").assertExists()
        compose.onNodeWithText("Every 3 months").assertExists()
    }

    @Test fun theEditorChecksTheNameAndLink() {
        seed("Filter", startedDaysAgo = 1, everyDays = 30)
        launch()
        compose.onNodeWithContentDescription("Add item").performClick()
        compose.onNodeWithTag("custom-item").performClick()
        compose.onNodeWithTag("save").assertIsNotEnabled()

        compose.onNodeWithTag("name-field").performTextInput("Water filter")
        compose.onNodeWithTag("save").assertIsEnabled()

        compose.onNodeWithTag("reorder-field").performScrollTo().performTextInput("not a link")
        compose.onNodeWithTag("save").assertIsNotEnabled()
        compose.onNodeWithText("That doesn't look like a web address", substring = true).assertExists()

        compose.onNodeWithTag("reorder-field").performTextClearance()
        compose.onNodeWithTag("reorder-field").performTextInput("example.com/filters")
        compose.onNodeWithTag("save").assertIsEnabled().performClick()

        compose.waitForTag("item-Water filter")
        assertEquals("example.com/filters", graph.store.all.first { it.name == "Water filter" }.reorderLink)
    }

    @Test fun swipeToReplaceThenUndo() {
        val lenses = seed("Lenses", startedDaysAgo = 20, everyDays = 14, spares = 2)
        launch()
        compose.onNodeWithText("Overdue").assertExists()

        swipeRowRight("item-Lenses")
        poll { graph.store.item(lenses.id)!!.spareCount == 1 }
        compose.waitForText("Undo")
        compose.onNodeWithText("Undo").performClick()

        poll { graph.store.item(lenses.id)!!.spareCount == 2 }
        val restored = graph.store.item(lenses.id)!!
        assertEquals(lenses.startDate, restored.startDate)
        assertEquals(1, restored.logs.size)
        compose.onNodeWithText("Overdue").assertExists()

        // Replacing for real moves it to Upcoming.
        compose.waitFor { compose.onAllNodesWithText("Undo").fetchSemanticsNodes().isEmpty() }
        swipeRowRight("item-Lenses")
        poll { graph.store.item(lenses.id)!!.logs.size == 2 }
        compose.waitForText("Upcoming")
        assertEquals(2, graph.store.item(lenses.id)!!.logs.size)
    }

    @Test fun snoozeOnlyWhenNearlyDueAndPauseResumes() {
        seed("Toothbrush", startedDaysAgo = 0, everyDays = 30)
        seed("Lenses", startedDaysAgo = 13, everyDays = 14)
        launch()

        compose.onNodeWithTag("item-Toothbrush").performClick()
        compose.onNodeWithTag("snooze").assertDoesNotExist()
        compose.onNodeWithTag("pause").performClick()
        compose.waitForText("Reminders paused")
        compose.onNodeWithText("Resume").assertExists()
        compose.onNodeWithTag("pause").performClick()
        compose.waitFor { compose.onAllNodesWithText("Reminders paused").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Back").performClick()

        compose.onNodeWithTag("item-Lenses").performClick()
        compose.onNodeWithTag("snooze").performClick()
        compose.onNodeWithText("1 hour").performClick()
        compose.waitForText("Snoozed until", substring = true)
        assertNotNull(graph.store.all.first { it.name == "Lenses" }.snoozedUntil)
    }

    @Test fun replacingFromDetailAndRewindingFromHistory() {
        seed("Lenses", startedDaysAgo = 20, everyDays = 14)
        launch()
        compose.onNodeWithTag("item-Lenses").performClick()
        compose.onNodeWithTag("countdown").assert(hasContentDescription("Overdue by 6 days"))

        compose.onNodeWithTag("mark-replaced").performClick()
        compose.onNodeWithTag("save-replacement").performClick()
        compose.waitFor { compose.onAllNodesWithTag("history-entry").fetchSemanticsNodes().size == 2 }
        compose.onNodeWithTag("countdown").assert(hasContentDescription("Due in 14 days"))

        // The newest entry is first; deleting it rewinds the countdown.
        val newest = compose.onAllNodesWithTag("history-entry")[0]
        newest.performScrollTo()
        compose.onAllNodes(hasContentDescription("Delete entry from", substring = true))[0].performClick()
        compose.waitFor { compose.onAllNodesWithTag("history-entry").fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag("countdown").performScrollTo().assert(hasContentDescription("Overdue by 6 days"))
    }

    @Test fun deletingAsksFirst() {
        seed("Lenses", startedDaysAgo = 2, everyDays = 14)
        launch()
        compose.onNodeWithTag("item-Lenses").performClick()
        compose.onNodeWithTag("delete-item").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, graph.store.all.size)

        compose.onNodeWithTag("delete-item").performScrollTo().performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.waitForText("Nothing to track yet")
        assertTrue(graph.store.all.isEmpty())
    }

    @Test fun replacedItFromTheNotification() {
        val lenses = seed("Lenses", startedDaysAgo = 14, everyDays = 14, spares = 3)
        val manager = context.getSystemService(NotificationManager::class.java)
        val id = Reminders.notificationId(lenses.id)

        graph.reminders.showReminder(lenses.id, "due")
        poll { manager.activeNotifications.any { it.id == id } }
        val notification = manager.activeNotifications.first { it.id == id }.notification
        assertEquals(
            listOf("Replaced it", "Snooze 1 hour", "Remind me tomorrow"),
            notification.actions.map { it.title.toString() },
        )

        // Press the real "Replaced it" button's intent, as the system would.
        notification.actions[0].actionIntent.send()
        poll { graph.store.item(lenses.id)!!.spareCount == 2 }
        assertTrue(graph.store.item(lenses.id)!!.startDate > lenses.startDate)
        poll { manager.activeNotifications.none { it.id == id } }
    }
}
