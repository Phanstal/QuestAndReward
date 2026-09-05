package com.familyquest.domain.rules

import com.familyquest.domain.model.TaskRecurrence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class TaskRecurrenceRulesTest {
    private val shanghai = TimeZone.of("Asia/Shanghai")

    @Test
    fun `daily occurrence changes exactly at local four`() {
        assertEquals("daily:2026-08-24", key(TaskRecurrence.DAILY, "2026-08-25T03:59:59", shanghai))
        assertEquals("daily:2026-08-25", key(TaskRecurrence.DAILY, "2026-08-25T04:00:00", shanghai))
    }

    @Test
    fun `weekly occurrence changes on Monday at local four`() {
        assertEquals("weekly:2026-08-17", key(TaskRecurrence.WEEKLY, "2026-08-24T03:59:59", shanghai))
        assertEquals("weekly:2026-08-24", key(TaskRecurrence.WEEKLY, "2026-08-24T04:00:00", shanghai))
    }

    @Test
    fun `monthly occurrence handles month end and leap day`() {
        assertEquals("monthly:2024-02", key(TaskRecurrence.MONTHLY, "2024-03-01T03:59:59", shanghai))
        assertEquals("monthly:2024-03", key(TaskRecurrence.MONTHLY, "2024-03-01T04:00:00", shanghai))
        assertEquals("daily:2024-02-29", key(TaskRecurrence.DAILY, "2024-02-29T12:00:00", shanghai))
    }

    @Test
    fun `monthly completion keys retain the legacy first key and add stable slots`() {
        val instant = LocalDateTime.parse("2026-08-25T12:00:00").toInstant(shanghai)
        val periodKey = TaskRecurrenceRules.occurrenceKey(TaskRecurrence.MONTHLY, instant, shanghai)

        assertEquals(
            periodKey,
            TaskRecurrenceRules.completionOccurrenceKey(TaskRecurrence.MONTHLY, instant, shanghai, 1),
        )
        assertEquals(
            "$periodKey#8",
            TaskRecurrenceRules.completionOccurrenceKey(TaskRecurrence.MONTHLY, instant, shanghai, 8),
        )
        assertEquals(true, TaskRecurrenceRules.belongsToOccurrence(periodKey, periodKey))
        assertEquals(true, TaskRecurrenceRules.belongsToOccurrence("$periodKey#8", periodKey))
        assertEquals(false, TaskRecurrenceRules.belongsToOccurrence("$periodKey#9", periodKey))
        assertEquals(false, TaskRecurrenceRules.belongsToOccurrence("monthly:2026-07#8", periodKey))
    }

    @Test
    fun `yearly occurrence changes on January first at local four`() {
        assertEquals("yearly:2025", key(TaskRecurrence.YEARLY, "2026-01-01T03:59:59", shanghai))
        assertEquals("yearly:2026", key(TaskRecurrence.YEARLY, "2026-01-01T04:00:00", shanghai))
    }

    @Test
    fun `same instant uses device timezone`() {
        val instant = Instant.parse("2026-08-24T20:30:00Z")

        assertEquals("daily:2026-08-25", TaskRecurrenceRules.occurrenceKey(TaskRecurrence.DAILY, instant, shanghai))
        assertEquals(
            "daily:2026-08-24",
            TaskRecurrenceRules.occurrenceKey(TaskRecurrence.DAILY, instant, TimeZone.of("America/Los_Angeles")),
        )
    }

    @Test
    fun `dst transition still changes at valid local four`() {
        val newYork = TimeZone.of("America/New_York")

        assertEquals("daily:2026-03-07", key(TaskRecurrence.DAILY, "2026-03-08T03:59:59", newYork))
        assertEquals("daily:2026-03-08", key(TaskRecurrence.DAILY, "2026-03-08T04:00:00", newYork))
        assertEquals("daily:2026-11-01", key(TaskRecurrence.DAILY, "2026-11-01T04:00:00", newYork))
    }

    @Test
    fun `once occurrence never changes`() {
        assertEquals("once", key(TaskRecurrence.ONCE, "2026-01-01T00:00:00", shanghai))
        assertEquals("once", key(TaskRecurrence.ONCE, "2030-12-31T23:59:59", shanghai))
    }

    private fun key(recurrence: TaskRecurrence, localDateTime: String, zone: TimeZone): String {
        val instant = LocalDateTime.parse(localDateTime).toInstant(zone)
        return TaskRecurrenceRules.occurrenceKey(recurrence, instant, zone)
    }
}
